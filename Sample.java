
public class Sample {

    public static void main(String[] args) throws Exception{
	System.out.println("Main method ran");
	sleeper();
	sleeper();
	SleepyClass.expensiveOp();
    }

    public static void sleeper() throws Exception{
	Thread.sleep(10);
    }
}