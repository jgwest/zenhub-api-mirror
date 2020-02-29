
## Introduction

This project is an open source framework for writing large-scale ZenHub analytics tools, bots, automation tasks, and more, while avoiding the API rate limits of both ZenHub and ZenHub Enterprise.

Both ZenHub and GitHub make their data available through a well-documented REST API, in addition to making that data available through the web-based UI that most users are familiar with. However, the REST API for both services has a strict rate limit that restricts the number of requests that can be made per second.

As of this writing, the ZenHub API rate limit is about 100 requests per minute, or only 1.7 requests per second. For instance, if you wish to retrieve the ZenHub Issue data for 500 issues, this would take ~5 minutes. In contrast, the same set of requests using the ZenHub API Mirror would take less than 1 second.

This project, ZenHub API Mirror, actively requests and stores local copies of the resources exposed by the ZenHub REST API, including Boards, Dependencies, Epics, and Issues. These resources are requested intelligently, ensuring the local copy is up-to-date, while always remaining within the standard ZenHub API rate limit.

This project consists of two components:
- A server, **ZenHubApiMirrorService**, hosted within a Docker image which runs on your local machine/network.
- A Java client, **ZenHubApiMirrorClient**, which queries the ZenHubApiMirrorService and returns the requested ZenHub resources.

The **ZenHubApiMirrorServic**e indexes the ZenHub server API and stores the cache locally, making it much faster to retrieve these resources without sending HTTP requests over the public internet. 

Unlike the ZenHub server API, this service allows unlimited connections, which both reduces network latency and allows you to dramaticaly drive up the # of requests per second you can issue. You can now fully utilize your network bandwidth (or if hosted locally, fully saturate your HD I/O bandwidth)

Other applications:
- Allows you to run multiple independent ZenHub bots/automation tasks through a single ZenHub account 
- Daily backup of ZenHub resources (eg run a daily backup job against the ZenHub data volume, with restore left as an exercise to the reader)
- Supports both public ZenHub and ZenHub enterpise, as well as GitHub and GitHub enterprise.


## Setup

First you will need to setup a server based on the ZenHubApiServer Docker image, hosted on Docker Hub. Next, once running, the server will index the ZenHub API resources that you specify in the settings file.

After the server has finished indexing, add the `ZenHubApiMirrorClient` dependency to your Java project as a Maven dependency, and follow the example below to connect to the ZenHubApiMirror server.





## Start the ZenHub API mirror server from Docker

#### 1) Pull the `jgwest/zenhub-api-mirror` container image and clone this GitHub project

```
docker pull jgwest/zenhub-api-mirror
git clone https://github.com/jgwest/zenhub-api-mirror
cd zenhub-api-mirror
```

#### 2) Edit the ZenHub API mirror settings file

You will need to provide the server authentication credentials for ZenHub, and for GitHub, so that it can begin to mirror these resources.

Edit the `zenhub-api-mirror/ZenHubApiMirrorLiberty/zenhub-settings.yaml`. Details for each field are available in the YAML file. 

Example:
```yaml
---
zenhubServer: api.zenhub.com
zenhubApiKey: (your api token from ZenHub dashboard)
githubServer: github.com
githubUsername: myusername
githubPassword: mypassword

# Here we specify that we want to index the Eclipse organization (https://github.com/eclipse),
# but see the sample 'zenhub-settings.yaml' file for how to instead index a user,
# or a set of individual repositories.
orgList:
- eclipse

presharedKey: (an arbitrary string, should be a long alphanumeric value, similar to a password it must between equivalent the mirror client and mirror server)
# dbPath: (dbPath is not required if running from Docker image)
```

#### 3) Run the Docker image

Run the following shell script to create the data volumes and start the container image.
```
resources/docker/run.sh
```

Or run the following command:
```shell
docker run  -d  -p 9443:9443 --name zenhub-api-mirror-container \
    -v zenhub-api-mirror-data-volume:/home/default/data \
    -v (path to your zenhub-settings.yaml):/config/zenhub-settings.yaml \
    -v zenhub-api-mirror-config-volume:/config \
    --restart always \
    --cap-drop=all \
    --tmpfs /opt/ol/wlp/output --tmpfs /logs \
    --read-only \
    jgwest/zenhub-api-mirror
```

You can watch the application server start, and begin to index the API resources, using the `docker logs -f (container id)` command. Any configuration errors will appear here as well.


## Build the client API, add the client library Maven dependency, then connect to the mirror server

First, build the main client dependency:
```shell
git clone https://github.com/jgwest/zenhub-api-java-client
cd zenhub-api-java-client
mvn install -DskipTests
```

Next, build the client itself:
```shell
git clone https://github.com/jgwest/zenhub-api-mirror
cd zenhub-api-mirror
mvn clean install -DskipTests
```

In order to connect to the ZenHubApiMirrorService server from your application, you will need to add the Maven dependency to your app, then connect to your `ZenHubApiMirrorService` server from the `ZenHubApiMirrorClient`. 

```xml
<dependency>
	<groupId>zenhub-api-mirror</groupId>
	<artifactId>ZenHubApiMirrorClient</artifactId>
	<version>1.0.0</version>
</dependency>
```

This project assumes a familiarity with the [ZenHub API](https://github.com/ZenHubIO/API). The resources exposed through the ZenHub API are mirrored by the `ZenHubApiMirrorService` server (hosted by Docker), and made available through the `ZenHubApiMirrorClient` client library. The `Service` classes in the `com.zhapi.client` package of `ZenHubApiMirrorClient` correspond to the ZenHub resources exposed by the ZenHub API.

For example, the following code snippet will connect to the `ZenHubApiMirrorService` and print all of the issues, in all of the pipelines, for a specific GitHub repository.

```java
ZenHubMirrorApiClient zhmac = new ZenHubMirrorApiClient("https://URL of your docker server", "preshared key from server settings file");

BoardService boardService = new BoardService(zhmac);

GetBoardForRepositoryResponseJson response = boardService.getZenHubBoardForRepo(/*githubRepositoryId*/)
		.getResponse();

response.getPipelines().forEach(pipeline -> {
	System.out.println("Pipeline: "+pipeline.getName());

	pipeline.getIssues().forEach(issue -> {
		System.out.println(issue.getPosition() + ") " + issue.getIssue_number() + " is-epic: " + issue.isIs_epic());
	});
});
```


## Docker image hardening

The `zenhub-api-mirror` container image is fully hardened against compromise using standard Docker and Linux hardening measures. 

- **Security-hardened**
  - *Run as non-root user*: Container runs as non-root user, to mitigate Linux kernel exploits that seek to escape the container through Linux kernel bugs.
  - *Read-only filesystem*: Application is mounted as a read-only file system to prevent and mitigate container compromise.
  - *Drop all container capabilities*: All kernel capabilities are dropped, which closes another door for malicious attemps to escaping the container through potential compromise of the Linux kernel.
  
The ZenHubAPIMirrorService is built on [OpenLiberty](https://openliberty.io), an open source enterprise-grade application server from IBM.

