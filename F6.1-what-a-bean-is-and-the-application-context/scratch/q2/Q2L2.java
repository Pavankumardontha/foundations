class PdfWriter {
    private final String folder;

    PdfWriter(String folder) {
        this.folder = folder;
    }

    void write(String name) {
        System.out.println("writing " + folder + "/" + name + ".pdf");
    }
}

class ReportService {
    private final PdfWriter writer = new PdfWriter("/reports/final");

    void monthlyReport() {
        writer.write("october");
    }
}

public class Q2L2 {
    public static void main(String[] args) {
        ReportService service = new ReportService();
        service.monthlyReport();
    }
}
