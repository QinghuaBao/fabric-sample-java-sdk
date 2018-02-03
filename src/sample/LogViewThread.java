package sample;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Created by bqh on 2018/2/2.
 * <p>
 * E-mail:M201672845@hust.edu.cn
 */
public class LogViewThread extends Thread{
    private Scanner scanner;
    private TextArea logMessage;
    public LogViewThread(TextArea logMessage) {
        try {
            scanner = new Scanner(new File("logs/propertieslogs.log"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        this.logMessage = logMessage;
    }

    @Override
    public void run(){
//        StringBuilder stringBuilder = new StringBuilder();
        while (true){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (scanner.hasNextLine()){
                Platform.runLater(()->logMessage.appendText(scanner.nextLine() + "\n"));
            }
        }
    }

}
