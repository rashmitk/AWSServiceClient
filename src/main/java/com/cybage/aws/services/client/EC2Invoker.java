package com.cybage.aws.services.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.util.Base64;


/**
 * Created by rashmitr on 8/16/2017.
 */

/**
 * This Lambda gets triggered by S3 Event after FailedRuleCheckerLambda PUT a failed rule file on S3
 * S3 Location - my-lambda/staging/FailedRuleReports/filename-name.properties
 *
 * <p/>
 * Create following environment variables for this Lambda with suitable values
 * <p/>
 * env=[staging]|[production]
 * ruleExecutionAppJAR=ruleExecutionApp-1.0-SNAPSHOT.jar
 */
public class EC2Invoker implements RequestHandler<S3Event, Object> {

    private static final Logger logger = LoggerFactory.getLogger(RuleExecutionInvokerLambdaHandler.class);

    ApplicationConstants applicationConstants = null;
    @Override
    public String handleRequest(S3Event s3Event, Context context) {

        String s3FilePath;
        String env;
        String failedRuleMappingIdFileName;
        List<String> failedRuleMappingIdsOnS3;

        String ruleExecutionAppJarName;
        String s3BucketName;
        String ec2Region;
        String ec2AMIId;
        String ec2InstanceType;
        String ec2SubnetId;
        String ec2KeyPair;
        String ec2IAMProfileName;
        String ec2SecurityGroupId;
        String ec2UserDataScript;
        String ec2TerminateNthMinute;
        String efsMountFS;

        Region region;
        AmazonEC2Client amazonEC2Client;

        RunInstancesResult result;
        RunInstancesRequest runInstancesRequest;

        final String BLANK_SPACE=" ";


        try {
            logger.info("Inside handleRequest() method");
            logger.info("S3Event : " + s3Event);
            env = System.getenv("environment");
            logger.info("env : " + env);

            if (s3Event.getRecords() != null && env != null && s3Event.getRecords().size() > 0) {
                s3FilePath = s3Event.getRecords().get(0).getS3().getObject().getKey();
                logger.info("s3FilePath : " + s3FilePath);
                failedRuleMappingIdFileName = s3FilePath.substring(s3FilePath.lastIndexOf('/') + 1, s3FilePath.length());
                logger.info("failedRuleMappingId File Name : " + failedRuleMappingIdFileName);



                applicationConstants = new ApplicationConstants(env);
                logger.info("Loading properties from S3");
                applicationConstants.loadPropertiesFromS3();

                logger.info("Load failed rule mapping ids from S3 report");
                failedRuleMappingIdsOnS3 = loadFailedRuleIdsFromS3(failedRuleMappingIdFileName);

                logger.info("env : " + env + ", failedRuleMappingIdsOnS3: " + failedRuleMappingIdsOnS3);

                if (env == null || env.length() < 0 || failedRuleMappingIdsOnS3 == null || failedRuleMappingIdsOnS3.isEmpty()) {
                    throw new Exception("No rule mapping id found in file stored on S3, Terminating the execution");
                }


                s3BucketName = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.reports.s3.bucket.name");
                logger.info("S3 Bucket from where to download RuleExecution App JAR : " + s3BucketName);
                ruleExecutionAppJarName= ApplicationConstants.properties.getProperty("failed.ruleExecution.app.jar.name");
                logger.info("RuleExecution App JAR Name : " + ruleExecutionAppJarName);
                efsMountFS = ApplicationConstants.properties.getProperty("failed.ruleExecution.ec2.efs.mount.fs");
                logger.info("EC2 EFS Mount Path : " + efsMountFS);
                ec2Region = ApplicationConstants.properties.getProperty("region");
                logger.info("EC2 Region : " + ec2Region);
                ec2AMIId = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.ec2.ami.id");
                logger.info("EC2 AMI Id : " + ec2AMIId);
                ec2InstanceType = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.ec2.instance.type");
                logger.info("EC2 Instance Type : " + ec2InstanceType);
                ec2IAMProfileName = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.ec2.iam.profile.name");
                logger.info("EC2 IAM Profile Name : " + ec2IAMProfileName);
                ec2SubnetId = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.ec2.subnet.id");
                logger.info("EC2 Subnet Id : " + ec2SubnetId);
                ec2KeyPair = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.ec2.keypair");
                logger.info("EC2 Keypair Name : " + ec2KeyPair);
                ec2SecurityGroupId = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.ec2.security.group.id");
                logger.info("EC2 SecurityGroup : " + ec2SecurityGroupId);
                ec2TerminateNthMinute = ApplicationConstants.properties.getProperty("failed.ruleExecution.invoker.lambda.ec2.terminate.ec2.cron.job.schedule.minute");
                logger.info("ec2TerminateNthMinute : " + ec2TerminateNthMinute);

                ec2UserDataScript = "#!/bin/bash\n" +
                        "sudo yum update -y\n" +
                        "sudo yum install -y nfs-utils\n" +
                        //"sudo mkdir efs\n" +
                        "cd /home/ec2-user/rashmit\n" +
                        efsMountFS + "\n" +
                        "sudo crontab -l > terminate_ec2_cron\n"+
                        "sudo echo \""+ec2TerminateNthMinute+" * * * * /home/ec2-user/rashmit/script/terminate_ec2.sh\" >> terminate_ec2_cron\n"+
                        "sudo crontab terminate_ec2_cron\n"+
                        //"sudo yum install java-1.8.0 -y\n" +
                        //"sudo rm ruleExecutionApp*.jar\n" +
                        "sudo aws s3 cp s3://"+s3BucketName+"/build/"+ruleExecutionAppJarName+BLANK_SPACE+".\n" +
                        "sudo chmod 777 "+ruleExecutionAppJarName+"\n" +
                        failedRuleMappingIdsOnS3.stream().map(id -> "sudo java  -jar "+ruleExecutionAppJarName+BLANK_SPACE+id +BLANK_SPACE+AWSConstants.environmentMap.get(env)).collect(Collectors.joining("\n"));


                amazonEC2Client = new AmazonEC2Client();
                logger.info("AmazonEC2Client created");
                region = Region.getRegion(Regions.fromName(ec2Region));
                logger.info("EC2 Region : " + ec2Region);
                amazonEC2Client.setEndpoint(region.getServiceEndpoint("ec2"));
                logger.info("AmazonEC2Client endpoint configured");


                IamInstanceProfileSpecification instanceIAMProfile = new IamInstanceProfileSpecification();
                instanceIAMProfile.setName(ec2IAMProfileName);

                /**
                 * STAGING ENV ONLY
                 *
                 * Below configuration should only be used when its require to auto-assign Public IP address to EC2 upon launch
                 * For Staging env deployment, Below configuration can be kept uncommented
                 * For Production env deployment, Below configuration should be commented
                 *
                 */

/*
                InstanceNetworkInterfaceSpecification networkInterfaceSpecification = new InstanceNetworkInterfaceSpecification();
                networkInterfaceSpecification.withGroups(ec2SecurityGroupId);
                networkInterfaceSpecification.setAssociatePublicIpAddress(true);
                networkInterfaceSpecification.setSubnetId(ec2SubnetId);
                networkInterfaceSpecification.setDeviceIndex(0);
*/

                //End of configuration for Auto-assign Public IP address to EC2 upon launch

                runInstancesRequest =
                        new RunInstancesRequest();

                logger.info("Setting configuration params for EC2 instance");
                logger.info("----------------------------------------------");
                logger.info("EC2 Region : " + ec2Region);
                logger.info("EC2 AMI Id : " + ec2AMIId);
                logger.info("EC2 Instance Type : " + ec2InstanceType);
                logger.info("EC2 Key-pair Name : " + ec2KeyPair);
                logger.info("EC2 IAM Profile Name : " + instanceIAMProfile.getName());
                logger.info("EC2 Subnet Id : " + ec2SubnetId);
                logger.info("EC2 SecurityGroupId : " + ec2SecurityGroupId);
                logger.info("EC2 ec2 User Data Script : " + ec2UserDataScript);
                logger.info("EC2 EFS Mount Path : " + efsMountFS);
                logger.info("RuleExecutionApp JAR : " + ruleExecutionAppJarName);
                logger.info("----------------------------------------------");

                /**
                 * PRODUCTION ENV ONLY
                 * Below configuration is for Production env deployment
                 */


                runInstancesRequest.withImageId(ec2AMIId)
                        .withInstanceType(ec2InstanceType)
                        .withSubnetId(ec2SubnetId)
                        .withSecurityGroupIds(ec2SecurityGroupId)
                        .withMinCount(1)
                        .withMaxCount(1)
                        .withKeyName(ec2KeyPair)
                        .withUserData(Base64.encodeAsString(ec2UserDataScript.getBytes()))
                        .withIamInstanceProfile(instanceIAMProfile);


                /**
                 * STAGING ENV ONLY
                 *
                 * Open below configuration for Staging env deployment
                 * and comment above Production env configuration
                 *
                 * Below configuration is for Staging environment only
                 */

/*
                runInstancesRequest.withImageId(ec2AMIId)
                        .withInstanceType(ec2InstanceType)
                        .withNetworkInterfaces(networkInterfaceSpecification)
                        .withMinCount(1)
                        .withMaxCount(1)
                        .withKeyName(ec2KeyPair)
                        .withUserData(Base64.encodeAsString(ec2UserDataScript.getBytes()))
                        .withIamInstanceProfile(instanceIAMProfile);
*/


                logger.info("Before starting EC2 instance");

                result = amazonEC2Client.runInstances(
                        runInstancesRequest);
                logger.info("EC2 instance started successfully to process failed rule by RuleExecution App");
                logger.info("Exiting handleRequest() method");

            }else {

                    logger.error("Mandatory input data is not available in S3 event, Terminating the execution with error");
                    logger.info("Exiting handleRequest() with error");
                    throw new Exception("Mandatory input data is not available in S3 event");
                }
            }catch(Exception e){
                logger.error("Error caught while Processing Request : {}", e.getMessage(), e);
                e.printStackTrace();
                //return false;
            }
        return "true";
    }

        public List<String> loadFailedRuleIdsFromS3(String failedRuleFileName) throws Exception {
            String failedIds;
            List<String> failedRuleMappingIds;
            try {
                // Load fail rule file from S3
                logger.info("Started loadFailedRuleIdsFromS3()");
                logger.info("File to load from S3 :{}",failedRuleFileName);
                failedIds = applicationConstants.loadFailedRuleIdsFromS3(failedRuleFileName);
                logger.info("File loaded successfully from S3");
                logger.info("List of failed Ids : {}",failedIds);
                failedRuleMappingIds = Arrays.asList(failedIds.split(","));
            } catch (Exception ex) {
                logger.error("Exception occurred while loading failedRuleFile from S3 to EFS : " + ex.getMessage());
                throw ex;
            }
            logger.info("End of loadFailedRuleIdsFromS3()");
            return failedRuleMappingIds;
        }



}
