# Processing of Wikipedia dumps with Hadoop

## General

### Hadoop

The processing of Wikpedia dump files uses Hadoop `3.*` (latest tested version is `3.3.1` from `2022-02-10`), which must be downloaded and installed according to your environment. The processing should still work with Hadoop `2`. In your Hadoop installation, some config files under `etc/hadoop/` might need to be adjusted for the task, we provide below our configuration files, but the spotted number can of course be adapted to take advantage of more CPU and memory for a more modern machine. 

Please note that running Hadoop is not straightfoward and involves often fixing various setup and connection problems (even on very standard and basic Linux installation). The problems depends on the environment (e.g. Linux distribution) and OS version. See [bellow](hadoop.md#common-issues-with-hadoop) for common failures and help.

### Expected produced files

For each language, there will be 12 generated csv files by this processing step. 

```
articleParents.csv   label.csv       pageLinkOut.csv              
categoryParents.csv  page.csv        redirectSourcesByTarget.csv  
childArticles.csv    pageLabel.csv   redirectTargetsBySource.csv
childCategories.csv  pageLinkIn.csv  stats.csv
```

### Expected Runtime

Intel Core i7-4790K CPU 4.00GHz Haswell, 32GB memory, with 4 cores, 8 threads, SSD, pseudo distributed mode:

* English Wikipedia XML dump: around 7 h 30

* French and German Wikipedia XML dump: around 2 h 30 mn

* other languages: between 30mn and 1h30

Note: at the present time, the process works only in pseudo distributed mode (LMDB cache DB are located under local /tmp/). For cluster level, we would need to locate the LMDB databases for cache on the HDFS and uses distributed haddop caches to access to the cache dbs. However, as the runtime on a single machine is very reasonable, we have not further generalized the process. 

## Configuration files for YARN

We give here the Hadoop `3.*` config files with YARN that we are using to process successfully the Wikidata and Wikipedia dumps. You can adapt them according to the capacity of your server. 

* `etc/hadoop/hadoop-env.sh`

```sh
export JAVA_HOME=/usr/lib/jvm/java-8-oracle
export HADOOP_PREFIX=/home/lopez/tools/hadoop/hadoop-3.3.1
export HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-"/home/lopez/tools/hadoop/hadoop-3.3.1/etc/hadoop"}
```


* `etc/hadoop/hdfs-site.xml`

```xml
  <property>
     <name>dfs.replication</name>
     <value>1</value>
  </property>
  
  <property>
     <name>dfs.name.dir</name>
     <value>/home/lopez/tools/hadoop/hadoop-3.3.1/mydata/hdfs/namenode/</value>
  </property>

  <property>
     <name>dfs.data.dir</name>
     <value>/home/lopez/tools/hadoop/hadoop-3.3.1/mydata/hdfs/datanode/</value>
  </property>
```

* `etc/hadoop/yarn-site.xml`
```xml
    <property>
        <name>yarn.nodemanager.aux-services</name>
        <value>mapreduce_shuffle</value>
    </property>
    <property>
      <name>yarn.nodemanager.resource.memory-mb</name>
      <value>13312</value> <!-- typical total memory available on the machine having 16GB RAM, in pseudo ditributed mode -->
    </property>
    <property>
      <name>yarn.scheduler.minimum-allocation-mb</name>
      <value>1024</value>
    </property>
    <property>
      <name>yarn.scheduler.maximum-allocation-mb</name>
      <value>12288</value>
    </property>
    <property>
        <name>yarn.nodemanager.vmem-check-enabled</name>
        <value>false</value>
    </property>
    <property>
        <name>yarn.nodemanager.pmem-check-enabled</name>
        <value>false</value>
    </property>
     <property>
        <name>yarn.nodemanager.resource.cpu-vcores</name>
        <value>4</value> <!-- indicate the number of cores available on the used machine, in pseudo ditributed mode -->
    </property>

// Should the job being scheduled but not executed, might due to lack of disk space on namenode or datanode.
// This can be avoided by adding to the yarn-site.xml:

  <property>
     <name>yarn.nodemanager.disk-health-checker.min-healthy-disks</name>
     <value>0.0</value>
  </property>
  <property>
     <name>yarn.nodemanager.disk-health-checker.max-disk-utilization-per-disk-percentage</name>
     <value>100.0</value>
  </property>
```
    

* `etc/hadoop/mapred-site.xml`

```xml
<configuration>
  <!--property>
    <name>mapred.child.java.opts</name>
    <value>-Xmx1024m</value>
  </property--> <!-- in case we need more memory in the main hadoop job, it should not be the case -->
  <property>
    <name>mapreduce.framework.name</name>
    <value>yarn</value>
  </property>
  <property>
    <name>mapreduce.map.memory.mb</name>
    <value>3072</value>
  </property>
  <property>
    <name>mapreduce.reduce.memory.mb</name>
    <value>8192</value> <!-- single reduce job ! -->
  </property>
  <property>
    <name>mapreduce.map.java.opts</name>
    <value>-Xmx1024m</value>
  </property>
  <property>
    <name>mapreduce.reduce.java.opts</name>
    <value>-Xmx8192m</value>
  </property>

  <property>
    <name>mapreduce.job.ubertask.enable</name>
    <value>true</value>
  </property>
  
  <property>
    <name>mapreduce.tasktracker.map.tasks.maximum</name>
    <value>4</value>
  </property>
  <property>
    <name>mapreduce.tasktracker.reduce.tasks.maximum</name>
    <value>1</value>
  </property> <!-- single reduce job ! -->
  <property>
    <name>mapred.reduce.slowstart.completed.maps</name>
    <value>1</value>
  </property>

  <property>
  	<name>mapred.task.timeout</name>
  	<value>1800000</value>
  </property> <!-- timout 30 minutes, safer for building the largest LMDB caches -->

  <property>
    <name>yarn.app.mapreduce.am.env</name>
    <value>HADOOP_MAPRED_HOME=${HADOOP_HOME}</value>
  </property>
  <property>
    <name>mapreduce.map.env</name>
    <value>HADOOP_MAPRED_HOME=${HADOOP_HOME}</value>
  </property>
  <property>
    <name>mapreduce.reduce.env</name>
    <value>HADOOP_MAPRED_HOME=${HADOOP_HOME}</value>
  </property>

</configuration>
```

* `etc/hadoop/core-site.xml`

```xml
<configuration>
  <property>
    <name>fs.defaultFS</name>
    <value>hdfs://localhost:9000</value>
  </property>
</configuration>
```

## Preparing the hdfs space:

Note: you will probably encounter some issues for starting Hadoop, see [bellow](hadoop.md#common-issues-with-hadoop) for help.

* Prepare the namenode:

```console
hadoop-3.3.1/bin/hdfs namenode -format
```

* start the HDFS nodes and copy the files to be processed on the HDFS:

```console
hadoop-3.3.1/sbin/start-dfs.sh

hadoop-3.3.1/bin/hdfs dfs -mkdir /user

hadoop-3.3.1/bin/hdfs dfs -mkdir /user/lopez

hadoop-3.3.1/bin/hdfs dfs -put ~/grisp/nerd-data/data/languages.xml /user/lopez/

hadoop-3.3.1/bin/hdfs dfs -put /mnt/data/wikipedia/latest/enwiki-latest-pages-articles-multistream.xml /user/lopez/

hadoop-3.3.1/bin/hdfs dfs -put /mnt/data/wikipedia/latest/frwiki-latest-pages-articles-multistream.xml /user/lopez/

hadoop-3.3.1/bin/hdfs dfs -put /mnt/data/wikipedia/latest/dewiki-latest-pages-articles-multistream.xml /user/lopez/

...

hadoop-3.3.1/bin/hdfs dfs -mkdir /user/lopez/output

hadoop-3.3.1/bin/hdfs dfs -mkdir /user/lopez/working
```

Note that the `**wiki-latest-pages-articles.xml` file must be passed **uncompressed** to hadoop. While `bzip2` compression format is normally supported automatically by Hadoop as input format (because it is a splitable compression format), it is currently not working with the Wikipedia dump file. 

## Common issues with Hadoop

Starting hadoop commonly fails for various reasons, we try to cover here the most common ones:

- **password-less authentication is not configured on localhost**:

```
Starting namenodes on [localhost]
localhost: user@localhost: Permission denied (publickey,password).
```

This can be solved by adding the ssh key of the machine to itself:

```bash
cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
```

- **JAVA_HOME not found**:

```
Starting namenodes on [localhost]
localhost: ERROR: JAVA_HOME is not set and could not be found.
```

Even if the `JAVA_HOME` is correctly set for the user, in the .bashrc or profile, for some unknown reasons Hadoop might fail to find and use it. To fix the issue, the `JAVA_HOME` needs to be set in the `hadoop/hadoop-env.sh` config file, as indicated in the previous section on configuration files: 

```bash
export JAVA_HOME=/usr/lib/jvm/<jdk folder>
```

- **Connection refused**:

```
Call From **hostname**/127.0.1.1 to localhost:9000 failed on connection exception: java.net.ConnectException: Connection refused; For more details see:  http://wiki.apache.org/hadoop/ConnectionRefused
```

Check if you have already a process listening to port `:9000`:

```
sudo netstat -tnlp | grep :9000
```

If yes, you need to stop this indicated process - this is usually a YARN zombi process that failed to stopped. 

- **Connextion to ResourceManager fails**:

When starting some Hadoop job, you might see:

```
2022-12-21 16:10:03,762 INFO client.DefaultNoHARMFailoverProxyProvider: Connecting to ResourceManager at /0.0.0.0:8032
2022-12-21 16:10:04,810 INFO ipc.Client: Retrying connect to server: 0.0.0.0/0.0.0.0:8032. Already tried 0 time(s); retry policy is RetryUpToMaximumCountWithFixedSleep(maxRetries=10, sleepTime=1000 MILLISECONDS)
```

First, just to be sure, check that you have started YARN:

```
sbin/start-yarn.sh
```

To solve this problem, you usually need to indicate in the `etc/hadoop/yarn-site.xml` configuration file:

```xml
<property>
  <name>yarn.resourcemanager.address</name>
  <value>127.0.0.1:8032</value>
</property>
<property>
  <name>yarn.resourcemanager.scheduler.address</name>
  <value>127.0.0.1:8030</value>
</property>
<property>
  <name>yarn.resourcemanager.resource-tracker.address</name>
  <value>127.0.0.1:8031</value>
</property>
```


## Building the hadoop job jar 

Under `~/grisp/nerd-data`:

```console
mvn clean package
```

will create job jar under `./target/com.scienceminer.grisp.nerd-data-0.0.5-job.jar` to be used below.

## Launching hadoop process: 

* start YARN:

```console
hadoop-3.3.1/sbin/start-yarn.sh
```

* English (path in HDFS, except the jar):

```console
hadoop-3.3.1/bin/hadoop jar ~/grisp/nerd-data/target/com.scienceminer.grisp.nerd-data-0.0.5-job.jar /user/lopez/enwiki-latest-pages-articles-multistream.xml /user/lopez/languages.xml en /user/lopez/working /user/lopez/output
```

After a few hours, when done, getting the csv files for the English language:

```console
hadoop-3.3.1/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/en/
```

* French:

```console
hadoop-3.3.1/bin/hadoop jar ~/grisp/nerd-data/target/com.scienceminer.grisp.nerd-data-0.0.5-job.jar /user/lopez/frwiki-latest-pages-articles-multistream.xml /user/lopez/languages.xml fr /user/lopez/working /user/lopez/output
```

Getting the csv files for French:

```console
hadoop-3.3.1/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/fr/
```

* German:

```console
hadoop-3.3.1/bin/hadoop jar ~/grisp/nerd-data/target/com.scienceminer.grisp.nerd-data-0.0.5-job.jar /user/lopez/dewiki-latest-pages-articles-multistream.xml /user/lopez/languages.xml de /user/lopez/working /user/lopez/output
```

Getting the csv files for German: 

```console
hadoop-3.3.1/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/de/
```

* Italian:

```console
hadoop-3.3.1/bin/hadoop jar ~/grisp/nerd-data/target/com.scienceminer.grisp.nerd-data-0.0.5-job.jar /user/lopez/itwiki-latest-pages-articles-multistream.xml /user/lopez/languages.xml it /user/lopez/working /user/lopez/output
```

Getting the csv files for Italian:

```console
hadoop-3.3.1/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/it/
```

* Spanish:

```console
hadoop-3.3.1/bin/hadoop jar ~/grisp/nerd-data/target/com.scienceminer.grisp.nerd-data-0.0.5-job.jar /user/lopez/eswiki-latest-pages-articles-multistream.xml /user/lopez/languages.xml es /user/lopez/working /user/lopez/output
```
Getting the csv files for Spanish:

```bash
hadoop-3.3.1/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/es/
```

And so on for other supported languages.

Finally you can clean and stop HDFS and stop YARN:

```console
hadoop-3.3.1/bin/hdfs dfs -rm /user/lopez/*wiki-latest-pages-articles-multistream.xml
hadoop-3.3.1/bin/hdfs dfs -rm -r /user/lopez/output/*
hadoop-3.3.1/bin/hdfs dfs -rm -r /user/lopez/working/*
hadoop-3.3.1/sbin/stop-dfs.sh
hadoop-3.3.1/sbin/stop-yarn.sh
```
