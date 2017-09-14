package com.cybage.aws.services.client;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class SQSMessageProducer {

	public SQSMessageProducer() {
		System.out.println("Inside SQSMessageProducer constructor..");
	}

	private void sendMessage() {
		System.out.println("Inside sendMessage()");
		String myQueueUrl = "https://sqs.us-east-1.amazonaws.com/430909181035/cybtest";

		try {

			AmazonSQS sqs = AmazonSQSClientBuilder.standard().withRegion(Regions.US_EAST_1).build();

			System.out.println("===========================================");
			System.out.println("Sending Message to Amazon SQS");
			System.out.println("===========================================\n");

			System.out.println("Sending a message to MyQueue.\n");
            sqs.sendMessage(new SendMessageRequest(myQueueUrl, "This message is sent by Cybage"));


		} catch (AmazonServiceException ase) {
			System.out.println("Caught an AmazonServiceException, which means your request made it "
					+ "to Amazon SQS, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out.println("Caught an AmazonClientException, which means the client encountered "
					+ "a serious internal problem while trying to communicate with SQS, such as not "
					+ "being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage());
		}

	}

	public static void main(String[] args) {
		SQSMessageProducer producer = new SQSMessageProducer();
		producer.sendMessage();
	}

}
