INST_ID=`curl http://169.254.169.254/latest/meta-data/instance-id`
echo $INST_ID
AVAIL_ZONE=`curl http://169.254.169.254/latest/meta-data/placement/availability-zone`
echo $AVAIL_ZONE
aws ec2 terminate-instances --instance-ids $INST_ID --region `echo ${AVAIL_ZONE:: -1}`
