package sample;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by bqh on 2018/2/2.
 * <p>
 * E-mail:M201672845@hust.edu.cn
 */
public class LogViewThread extends Thread{
    private File file;
    private TextArea logMessage;
    private long lastTimeFileSize = 0; //上次文件大小
    public LogViewThread(TextArea logMessage) throws IOException {
        file = new File("logs/propertieslogs.log");
        this.logMessage = logMessage;
    }


    @Override
    public void run(){
//        try {
//            //获得变化部分的
//            randomFile.seek(lastTimeFileSize);
//            String tmp = "";
//            while( (tmp = randomFile.readLine())!= null) {
//                String x = new String(tmp.getBytes("ISO8859-1"));
//                Platform.runLater(()->logMessage.appendText(x));
//            }
//            lastTimeFileSize = randomFile.length();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        int line = 0;
        while (true){
            Scanner scanner = null;
            try {
                Thread.sleep(500);
                scanner = new Scanner(file);
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < line; i++) {
                scanner.nextLine();
            }
            if (scanner.hasNextLine()){
                String x = scanner.nextLine();
                Platform.runLater(()->logMessage.appendText(x + "\n"));
                line++;
            }
        }
    }

}
