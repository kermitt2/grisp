# Preprocessing of Wikipedia dumps with Hadoop

## General

### Expected produced files

For each language, there must be 12 generated csv files

```
articleParents.csv   label.csv       pageLinkOut.csv              
categoryParents.csv  page.csv        redirectSourcesByTarget.csv  
childArticles.csv    pageLabel.csv   redirectTargetsBySource.csv
childCategories.csv  pageLinkIn.csv  stats.csv
```

### Expected Runtime

Intel Core i7-4790K CPU 4.00GHz Haswell, 16GB memory, with 4 cores, 8 threads, SSD, pseudo distributed mode:

* English Wikipedia XML dump: around 7 h 30

* French and German Wikipedia XML dump: around 2 h 30 mn

Note: at the present time, the process works only in pseudo distributed mode (LMDB cache DB are located under local /tmp/). For cluster level, we would need to locate the LMDB databases for cache on the HDFS and uses distributed haddop caches to access to the cache dbs. However, as the runtime on a single machine is very reasonable, we have not further generalized the process. 

## Configuration files for YARN

Hadoop 2.* config with YARN: 

* `etc/hadoop/hadoop-env.sh`

```sh
export JAVA_HOME=/usr/lib/jvm/java-8-oracle
export HADOOP_PREFIX=/home/lopez/tools/hadoop/hadoop-2.7.4
export HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-"/home/lopez/tools/hadoop/hadoop-2.7.4/etc/hadoop"}
```


* `etc/hadoop/hdfs-site.xml`

```xml
  <property>
     <name>dfs.replication</name>
     <value>1</value>
  </property>
  
  <property>
     <name>dfs.name.dir</name>
     <value>/home/lopez/tools/hadoop/hadoop-2.7.4/mydata/hdfs/namenode/</value>
  </property>

  <property>
     <name>dfs.data.dir</name>
     <value>/home/lopez/tools/hadoop/hadoop-2.7.4/mydata/hdfs/datanode/</value>
  </property>

// etc/hadoop/yarn-site.xml
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
    <value>2048</value>
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
    <value>-Xmx7168m</value>
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

* Prepare the namenode:

> hdfs namenode -format

* start the HDFS nodes:

> ~/tools/hadoop/hadoop-2.7.4/sbin/start-dfs.sh

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -mkdir /user

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -mkdir /user/lopez

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -put ~/grisp/nerd-data/data/languages.xml /user/lopez/

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -put /mnt/data/wikipedia/latest/enwiki-latest-pages-articles.xml /user/lopez/

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -put /mnt/data/wikipedia/latest/frwiki-latest-pages-articles.xml /user/lopez/

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -put /mnt/data/wikipedia/latest/dewiki-latest-pages-articles.xml /user/lopez/

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -mkdir /user/lopez/output

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -mkdir /user/lopez/working



## Building the hadoop job jar 

Under `~/grisp/nerd-data`:

>  mvn clean install

will create job jar under `./target/com.scienceminer.grisp.nerd-data-0.0.4-job.jar` to be used below.

## Launching hadoop process: 

* start YARN:

> ~/tools/hadoop/hadoop-2.7.4//sbin/start-yarn.sh

* English (path in HDFS, except the jar):

> ~/tools/hadoop/hadoop-2.7.4/bin/hadoop jar com.scienceminer.grisp.nerd-data-0.0.4-job.jar /user/lopez/enwiki-latest-pages-articles.xml /user/lopez/languages.xml en /user/lopez/working /user/lopez/output

When done, getting the csv files for the English language:

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/en/

* French:

>  ~/tools/hadoop/hadoop-2.7.4/bin/hadoop jar com.scienceminer.grisp.nerd-data-0.0.4-job.jar /user/lopez/frwiki-latest-pages-articles.xml /user/lopez/languages.xml fr /user/lopez/working /user/lopez/output

Getting the csv files for French:

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/fr/

* German:

> ~/tools/hadoop/hadoop-2.7.4/bin/hadoop jar com.scienceminer.grisp.nerd-data-0.0.4-job.jar /user/lopez/dewiki-latest-pages-articles.xml /user/lopez/languages.xml de /user/lopez/working /user/lopez/output

Getting the csv files for German: 

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/de/

* Italian:

> ~/tools/hadoop/hadoop-2.7.4/bin/hadoop jar com.scienceminer.grisp.nerd-data-0.0.4-job.jar /user/lopez/itwiki-latest-pages-articles.xml /user/lopez/languages.xml it /user/lopez/working /user/lopez/output

Getting the csv files for Italian:

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/it/

* Spanish:

> ~/tools/hadoop/hadoop-2.7.4/bin/hadoop jar com.scienceminer.grisp.nerd-data-0.0.4-job.jar /user/lopez/eswiki-latest-pages-articles.xml /user/lopez/languages.xml es /user/lopez/working /user/lopez/output

Getting the csv files for Spanish:

> ~/tools/hadoop/hadoop-2.7.4/bin/hdfs dfs -get /user/lopez/output/* /mnt/data/wikipedia/latest/es/
