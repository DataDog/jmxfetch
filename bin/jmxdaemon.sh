#!/bin/bash

JAR_DIR="/tmp/jmx"
JMX_CONF_DIR="/tmp/jmx"

# It checks if config file exists for every java machine and returns the config file
function get_config_string(){
  local procs=$*
  local ret=""
  for p in $procs
  do
    if [ -a "$JMX_CONF_DIR/$p.yml.temp"  ]; then
       sed  "s/HHHH/$HOSTNAME/g" Bootstrap.yml.temp > $JMX_CONF_DIR/$p.yml
      ret="$ret $JMX_CONF_DIR/$p.yml"
    fi
  done
  echo $ret
}


while true
 do
   jmx_running_flag=$( ps aux | grep org.datadog.jmxfetch.App | grep -v grep | wc -l )
   if [ $jmx_running_flag = 0 ]; then
     java_procs=$( jps | awk  '{print $2}' )

     if [ $? = 0 ]; then
       java_count=$( echo $java_procs | wc -c )

       if [ $java_count -gt 2  ];then
         conf_files=`get_config_string $java_procs`
         echo "starting"
         cd $JAR_DIR ;nohup java -Xmx100m -cp "jmxfetch-0.3.0-jar-with-dependencies.jar:$JAVA_HOME/lib/tools.jar" org.datadog.jmxfetch.App -r javadog collect -L INFO -c $conf_files &
       fi
     fi
   else
     echo "no hago nada"
   fi
   sleep 300 # checkea cada 5 minutos
done
