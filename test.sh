javac Sample.java SleepyClass.java && 
java Sample && 
lein uberjar && 
java -cp profiling-injector-1.0.0-SNAPSHOT-standalone.jar:. profiling_injector.core Sample SleepyClass && 
java Sample > profile.txt
java -cp profiling-injector-1.0.0-SNAPSHOT-standalone.jar:. profiling_injector.analyze < profile.txt
