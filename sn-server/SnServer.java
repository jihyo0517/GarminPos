import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnServer {
    private static final Logger log = LoggerFactory.getLogger(SnServer.class);
    private static final int PORT = Integer.getInteger("snserver.port", 8000);
    private static final int COL_DATETIME = 0;
    private static final int COL_SN = 1;
    private static final int MAX_BODY_BYTES = 8 * 1024;   // SN 은 수십 바이트 — 비정상 요청 차단
    private static final int MAX_ROWS = 1000;             // 장기 실행 시 메모리 무한 증가 방지
    private static final String DISCOVER_REQ = "SN_DISCOVER_V1";
    private static final String DISCOVER_RESP = "SN_SERVER_V1";
    private static DefaultTableModel model;
    private static JTable table;
    private static JFrame frame;

    public static void main(String[] args) {
        // 느린/끊긴 클라이언트가 요청 스레드를 영원히 점유하지 못하게 (초 단위, 서버 생성 전에 설정)
        System.setProperty("sun.net.httpserver.maxReqTime", "10");
        System.setProperty("sun.net.httpserver.maxRspTime", "10");
        // 어디서 죽는지 추적 가능하도록: 모든 스레드의 미처리 예외와 프로세스 종료를 로그에 남김
        Thread.setDefaultUncaughtExceptionHandler((t, e) ->
                log.error("미처리 예외 (thread={})", t.getName(), e));
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                log.info("프로세스 종료 (shutdown hook)"), "shutdown-log"));

        String ip = localIp();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/scan", SnServer::handleScan);
            // 단일 디스패처 스레드 대신 소형 풀: 요청 하나가 막혀도 수신이 계속됨
            server.setExecutor(Executors.newFixedThreadPool(2));
            server.start();
        } catch (java.net.BindException e) {
            log.error("포트 {} 이 이미 사용 중입니다. 다른 SnServer 인스턴스를 닫고 다시 실행하세요.", PORT);
            JOptionPane.showMessageDialog(null,
                    "포트 " + PORT + " 이 이미 사용 중입니다.\n이미 실행 중인 SN 수신기가 있는지 확인하세요.",
                    "SN 수신기", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return; // unreachable, but satisfies compiler
        } catch (IOException e) {
            log.error("서버 시작 실패: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(null,
                    "서버 시작 실패: " + e.getMessage(), "SN 수신기", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        startDiscoveryResponder();
        log.info("Listening on http://{}:{}/scan", ip, PORT);
        SwingUtilities.invokeLater(() -> buildUi(ip));
    }

    private static void buildUi(String ip) {
        frame = new JFrame("SN 수신기 — " + ip + ":" + PORT);
        // 실수로 X 를 눌러 수신기가 조용히 죽는 것을 방지: 확인 후에만 종료 + 종료 로그
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                int r = JOptionPane.showConfirmDialog(frame,
                        "SN 수신기를 종료할까요?\n종료하면 태블릿 스캔을 받을 수 없습니다.",
                        "종료 확인", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) {
                    log.info("사용자가 창을 닫아 종료");
                    System.exit(0);
                }
            }
        });
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
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch (IllegalStateException e) {
            // Windows 에서 다른 프로그램이 클립보드를 잡고 있으면 발생 — 잠시 후 한 번 재시도
            try {
                Thread.sleep(100);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            } catch (Exception e2) {
                log.warn("클립보드 복사 실패: {}", text, e2);
            }
        }
    }

    private static void handleScan(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                log.warn("거부: {} (POST 아님)", ex.getRequestMethod());
                respond(ex, 405, "POST only");
                return;
            }
            String body = readBody(ex.getRequestBody());
            if (body == null) {
                log.warn("거부: 본문이 {} bytes 초과", MAX_BODY_BYTES);
                respond(ex, 413, "body too large");
                return;
            }
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

    /** 태블릿의 UDP 브로드캐스트("SN_DISCOVER_V1")에 응답해 IP 가 바뀌어도 스스로 찾게 한다. */
    private static void startDiscoveryResponder() {
        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try (DatagramSocket sock = new DatagramSocket(PORT)) {
                    log.info("UDP 탐색 응답 대기 (port {})", PORT);
                    byte[] buf = new byte[64];
                    while (true) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        sock.receive(p);
                        String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                        if (DISCOVER_REQ.equals(msg)) {
                            byte[] resp = DISCOVER_RESP.getBytes(StandardCharsets.UTF_8);
                            sock.send(new DatagramPacket(resp, resp.length, p.getAddress(), p.getPort()));
                            log.info("탐색 요청 응답: {}", p.getAddress().getHostAddress());
                        }
                    }
                } catch (Exception e) {
                    log.warn("UDP 탐색 응답기 오류, 5초 후 재시작", e);
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { return; }
                }
            }
        }, "discovery-responder");
        t.setDaemon(true);
        t.start();
    }

    private static long parseTs(String ts) {
        try { return Long.parseLong(ts); } catch (Exception e) { return System.currentTimeMillis(); }
    }

    private static void appendRow(String dateTime, String sn) {
        if (model == null) return;
        model.addRow(new Object[]{dateTime, sn});
        while (model.getRowCount() > MAX_ROWS) model.removeRow(0);
        // 새 행으로 자동 스크롤
        int last = model.getRowCount() - 1;
        table.scrollRectToVisible(table.getCellRect(last, 0, true));
    }

    /** 본문을 읽되 MAX_BODY_BYTES 초과 시 null. */
    private static String readBody(InputStream in) throws IOException {
        byte[] b = in.readNBytes(MAX_BODY_BYTES + 1);
        if (b.length > MAX_BODY_BYTES) return null;
        return new String(b, StandardCharsets.UTF_8);
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
