![DKSR-logo](https://user-images.githubusercontent.com/102658834/171163305-cdd99910-1b93-4d74-be88-7c1d23fdcf0d.png)

# DKSR Connector Example

## Prerequisites
In order to run the Connector following steps are required

### Create a new Connector
`POST /UrbanPulseManagement/api/connectors`
```json
{
  "description": {
    "name": "DKSRExampleConnector",
    "comment": "An example connector sending random numbers"
  },
  "key": "<SECRET>"
}
```

### Create a new EventType
`POST /UrbanPulseManagement/api/eventtypes`
```json
{
  "name": "DKSRExampleEventType",
  "description": {
    "SID": "The ID of the Sensor",
    "timestamp": "The date and time the corresponding event was fired",
    "value": "A random double for example purposes"
  },
  "config": {
    "SID": "string",
    "timestamp": "java.util.Date",
    "value": "double"
  }
}
```

### Create a new Sensor
`POST /UrbanPulseManagement/api/sensors`
```json
{
  "senderid": "<ID of the just created Connector>",
  "categories": [],
  "description": {
    "reference": {}
  },
  "location": {},
  "eventtype": "<ID of the just created EventType>"
}
```

## Run the Example Connector

At first, you will need to configure the Connector in order to run it. Therefor you need to edit the
supplied `config.json` in the root directory.
```json
{
  "receiver": {
    "host": "localhost",
    "port": 40777,      
    "useSsl": true,     
    "trustAll": false    
  },
  "sensorId": "<ID of the just created Sensor>",
  "interval": 15000,
  "credentials": {
    "connectorId": "<ID of the just created Connector>",
    "connectorKey": "<SECRET>"
  }
}
```
Make sure the `receiver` is properly configured in order to send events to the Inbound module of 
your UrbanPulse installation.

Once your connector is configured you can build and run the connector with following command.
```mvn package exec:java```

## Include jks file for communicating with the OUP Inbound 
Copy the jks file from the package/directory of OUP Core for the HTTPInbound Verticle. This path of the jks file is included as an argument in the pom.xml file. 

![image](https://user-images.githubusercontent.com/102675978/171160103-debc7eeb-3f58-4689-87fb-72df57d54b52.png)


## See the events sent by the Connector
To check whether UrbanPulse is receiving and processing incoming events properly, you can create a 
Statement on our `DKSRExampleEventType`.

### Create a new Statement
`POST /UrbanPulseManagement/api/statements`
```json
{
  "name": "DKSRExampleEventTypeStatement",
  "query": "SELECT * FROM DKSRExampleEventType"
}
```

### Create an UpdateListener for the Statement
`POST /UrbanPulseManagement/api/statements/<STATEMENT-ID>/update-listeners`
```json
{
  "target": "wss://localhost:3210/OutboundInterfaces/outbound/DKSRExampleEventTypeStatement"
}
```

### Show real-time events
To actually see the real-time events sent by the connector, go to 
https://localhost:3210/OutboundInterfaces/outbound/DKSRExampleEventTypeStatement

You should see something like
```
{"_headers":{"eventType":"DKSRExampleEventType"},"statementName":"DKSRExampleEventTypeStatement","value":0.38377175650459394,"timestamp":"2022-05-27T11:04:37.832+0000","SID":"8044ae80-2f1a-4eaf-b8ea-c91de19ec2cb"}
```
As per th config.json file, these events&JSON Data sets can be seen coming into the OUP Persistence and Outbound module every 15 seconds
