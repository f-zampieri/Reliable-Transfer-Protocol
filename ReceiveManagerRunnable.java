public class ReceiveManagerRunnable implements Runnable {

    private FXVSocket fxv;
    private Object lock;

    public ReceiveManagerRunnable(FXVSocket fxvSocket, Object lock) {
        this.fxv = fxvSocket;
        this.lock = lock;
    }

    public void run() {
    }
}


        


