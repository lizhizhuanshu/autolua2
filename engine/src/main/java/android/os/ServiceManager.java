package android.os;

public final class ServiceManager {
    public static IBinder getService(String name)
    {
        throw new RuntimeException();
    }
    public static void addService(String name,IBinder iBinder,boolean is){throw new RuntimeException();}
    public static String[] listServices(){throw new RuntimeException();}
}
