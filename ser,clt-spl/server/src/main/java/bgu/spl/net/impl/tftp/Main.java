package bgu.spl.net.impl.tftp;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Path myPath = Paths.get("").toAbsolutePath();
        Path parentPath = myPath;

        // Navigate up the directory structure 7 times
        // for (int i = 0; i < 7; i++) {
        //     parentPath = parentPath.getParent();
        // }

        // Construct the desired path
        Path path = parentPath.resolve("files");

        System.out.println(myPath.toString());
        System.out.println(path.toString());
    }
}
