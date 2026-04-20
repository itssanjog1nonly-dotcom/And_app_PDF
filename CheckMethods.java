import androidx.pdf.viewer.fragment.PdfViewerFragment;
import java.lang.reflect.Method;
public class CheckMethods {
    public static void main(String[] args) {
        for (Method m : PdfViewerFragment.class.getMethods()) {
            if (m.getName().toLowerCase().contains("save") || m.getName().toLowerCase().contains("edit") || m.getName().toLowerCase().contains("document")) {
                System.out.println(m.getName());
            }
        }
    }
}
