javac Sample.java SleepyClass.java && 
java Sample && 
lein jar && 
java -cp profiling-injector-1.0.0-SNAPSHOT.jar:lib/*:. profiling_injector.core Sample SleepyClass && 
java Sample > profile.txt
java -cp profiling-injector-1.0.0-SNAPSHOT:lib/*:. profiling_injector.analyze < profile.txt
