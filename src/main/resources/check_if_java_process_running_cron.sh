#!/bin/bash
count=$(ps -ef | grep -i adCopyGrammarRuleExecutionApp*|grep java|wc -l)
echo $count
while [ $count -lt 1 ]
do
        #echo "Welcome $count times"
        count=`expr $count + 1`
        java -jar /home/ec2-user/safetynet/adCopyGrammarRuleExecutionApp-1.0-SNAPSHOT.jar 2
        echo 'Inside Loop'
done
