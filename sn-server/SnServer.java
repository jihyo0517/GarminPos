import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnServer {
    private static final Logger log = LoggerFactory.getLogger(SnServer.class);
    private static final int PORT = 8000;
    private static final int COL_DATETIME = 0;
    private static final int COL_SN = 1;
    private static DefaultTableModel model;
    private static JTable table;
    private static JFrame frame;

    public static void main(String[] args) {
        String ip = localIp();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/scan", SnServer::handleScan);
            server.setExecutor(null);
            server.start();
        } catch (java.net.BindException e) {
            log.error("포트 {} 이 이미 사용 중입니다. 다른 SnServer 인스턴스를 닫고 다시 실행하세요.", PORT);
            System.exit(1);
            return; // unreachable, but satisfies compiler
        } catch (IOException e) {
            log.error("서버 시작 실패: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }
        log.info("Listening on http://{}:{}/scan", ip, PORT);
        SwingUtilities.invokeLater(() -> buildUi(ip));
    }

    private static void buildUi(String ip) {
        frame = new JFrame("SN 수신기 — " + ip + ":" + PORT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(520, 640);

        model = new DefaultTableModel(new Object[]{"날짜/시각", "시리얼"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(model);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        table.setRowHeight(28);
        table.getTableHeader().setReorderingAllowed(false);
        // 셀 단위 선택 → 바코드 열만 골라 Ctrl+C 하면 시각은 안 딸려옴
        table.setCellSelectionEnabled(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getColumnModel().getColumn(COL_DATETIME).setPreferredWidth(150);
        table.getColumnModel().getColumn(COL_DATETIME).setMaxWidth(200);
        table.getColumnModel().getColumn(COL_SN).setPreferredWidth(320);

        // Delete 키 → 선택 행 삭제
        table.getInputMap(JComponent.WHEN_FOCUSED)
             .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedRows");
        table.getActionMap().put("deleteSelectedRows", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { deleteSelectedRows(); }
        });

        // 누르는 순간 → 그 행 바코드 즉시 복사
        // (mouseClicked 는 미세하게 움직이면 드래그로 판정돼 안 불림 → mousePressed 사용)
        table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) copyToClipboard(String.valueOf(model.getValueAt(row, COL_SN)));
            }
        });

        JScrollPane scroll = new JScrollPane(table);

        JButton deleteSel = new JButton("선택삭제");
        deleteSel.addActionListener(e -> deleteSelectedRows());
        JButton clear = new JButton("전체지우기");
        clear.addActionListener(e -> model.setRowCount(0));
        JPanel buttons = new JPanel();
        buttons.add(deleteSel);
        buttons.add(clear);

        frame.add(scroll, BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    private static void deleteSelectedRows() {
        int[] rows = table.getSelectedRows();
        // 역순으로 제거해 인덱스가 밀리지 않게
        for (int i = rows.length - 1; i >= 0; i--) {
            model.removeRow(table.convertRowIndexToModel(rows[i]));
        }
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private static void handleScan(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                log.warn("거부: {} (POST 아님)", ex.getRequestMethod());
                respond(ex, 405, "POST only");
                return;
            }
            String body = readBody(ex.getRequestBody());
            log.debug("요청 본문: {}", body);
            Map<String,String> m = ScanFormat.parseFormBody(body);
            String sn = m.get("sn");
            if (sn == null || sn.isEmpty()) {
                log.warn("거부: sn 없음 (body={})", body);
                respond(ex, 400, "missing sn");
                return;
            }
            long ts = parseTs(m.get("ts"));
            String device = m.get("device");
            String dt = ScanFormat.formatDateTime(ts, ZoneId.systemDefault());
            log.info("스캔 수신 sn={} device={}", sn, device);
            final String snVal = sn;
            SwingUtilities.invokeLater(() -> appendRow(dt, snVal));
            respond(ex, 200, "OK");
        } catch (Exception e) {
            log.warn("잘못된 요청 처리 실패", e);
            respond(ex, 400, "bad request");
        }
    }

    private static long parseTs(String ts) {
        try { return Long.parseLong(ts); } catch (Exception e) { return System.currentTimeMillis(); }
    }

    private static void appendRow(String dateTime, String sn) {
        if (model == null) return;
        model.addRow(new Object[]{dateTime, sn});
        // 새 행으로 자동 스크롤
        int last = model.getRowCount() - 1;
        table.scrollRectToVisible(table.getCellRect(last, 0, true));
    }

    private static String readBody(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange ex, int code, String msg) throws IOException {
        byte[] b = msg.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress())
                        return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
