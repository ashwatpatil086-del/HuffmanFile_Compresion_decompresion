/* =====================================================================
 *  HUFFMAN FILE COMPRESSION SYSTEM  —  single-file GUI edition
 * =====================================================================
 *  The full-featured Swing GUI (drag-and-drop, Huffman Tree
 *  visualization, sortable Code Table, Data-Structures write-up tab)
 *  consolidated into one file for easy reading/explaining and a
 *  zero-setup run in VS Code.
 *
 *  RUN IT:
 *      java HuffmanGUI.java        (Java 11+, no compile step)
 *  or:
 *      javac HuffmanGUI.java
 *      java HuffmanGUI
 *
 *  SECTIONS IN THIS FILE (in reading order):
 *    1.  HuffmanGUI         - the window: header, tabs, drag & drop, wiring
 *    2.  ProgressListener   - callback interface for progress bars
 *    3.  HuffmanNode        - one node of the Huffman tree
 *    4.  MinHeap            - custom priority queue (array-based binary heap)
 *    5.  CustomHashMap      - custom hash table (separate chaining + resize)
 *    6.  HuffmanTree        - builds the tree, generates codes, (de)serializes it
 *    7.  FileHandler        - BitWriter / BitReader bit-level I/O
 *    8.  CompressionResult / DecompressionResult - data carried back to the GUI
 *    9.  HuffmanEncoder / HuffmanDecoder - the two pipelines
 *   10.  Theme              - shared color palette & fonts
 *   11.  RoundedButton      - custom-painted action button
 *   12.  TreePanel          - draws the Huffman tree
 *   13.  CodeTablePanel     - sortable symbol -> code table
 *   14.  AboutPanel         - the Data Structures Used write-up
 * ===================================================================== */

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * The application window: a styled header with the two main actions, a
 * drag-and-drop zone, an activity log, and three supporting tabs (Huffman
 * Tree visualization, Code Table, and the Data Structures write-up).
 */
public class HuffmanGUI extends JFrame {

    private final JTextArea logArea = new JTextArea();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Ready. Drop a file below, or use the buttons above.");
    private final RoundedButton compressButton = new RoundedButton("\uD83D\uDCE6  Compress File\u2026", Theme.PRIMARY, Theme.PRIMARY_HOVER);
    private final RoundedButton decompressButton = new RoundedButton("\uD83D\uDCC2  Decompress .huff File\u2026", Theme.ACCENT, Theme.ACCENT_HOVER);
    private final TreePanel treePanel = new TreePanel();
    private final CodeTablePanel codeTablePanel = new CodeTablePanel();
    private final JTabbedPane tabs = new JTabbedPane();

    private File lastDirectory = new File(System.getProperty("user.home"));

    public HuffmanGUI() {
        super("Huffman File Compression \u2014 Advanced Data Structures Project");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(920, 640);
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(null);
        setIconImage(buildAppIcon());
        getContentPane().setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);

        compressButton.addActionListener(e -> chooseFileToCompress());
        decompressButton.addActionListener(e -> chooseFileToDecompress());
    }

    // ---------------------------------------------------------------- header

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(Theme.BACKGROUND);
        header.setBorder(new EmptyBorder(20, 24, 12, 24));

        JLabel title = new JLabel("Huffman File Compression System");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_DARK);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Lossless compression backed by a custom min-heap, hash table, and binary trie.");
        subtitle.setFont(Theme.FONT_SUBTITLE);
        subtitle.setForeground(Theme.TEXT_MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 16));
        buttonRow.setBackground(Theme.BACKGROUND);
        buttonRow.add(compressButton);
        buttonRow.add(decompressButton);

        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(buttonRow);
        return header;
    }

    // ------------------------------------------------------------------ tabs

    private JComponent buildTabs() {
        tabs.setFont(Theme.FONT_BODY_BOLD);
        tabs.addTab("\u2328  Activity", buildHomeTab());
        tabs.addTab("\uD83C\uDF33  Huffman Tree", new JScrollPane(treePanel));
        tabs.addTab("\uD83D\uDCCB  Code Table", codeTablePanel);
        tabs.addTab("\uD83D\uDCD8  Data Structures Used", new AboutPanel());
        tabs.setBorder(new EmptyBorder(0, 12, 12, 12));
        return tabs;
    }

    private JComponent buildHomeTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));

        panel.add(buildDropZone(), BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(Theme.FONT_MONO);
        logArea.setMargin(new Insets(10, 10, 10, 10));
        logArea.setBackground(Theme.CARD_BG);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER), "Activity Log", 0, 0, Theme.FONT_BODY_BOLD, Theme.TEXT_DARK));
        panel.add(logScroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setBackground(Theme.BACKGROUND);
        statusLabel.setFont(Theme.FONT_BODY);
        statusLabel.setForeground(Theme.TEXT_MUTED);
        progressBar.setStringPainted(true);
        footer.add(statusLabel, BorderLayout.NORTH);
        footer.add(progressBar, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    private JComponent buildDropZone() {
        JPanel drop = new JPanel(new BorderLayout());
        drop.setBackground(Theme.CARD_BG);
        drop.setBorder(BorderFactory.createDashedBorder(Theme.PRIMARY, 2, 6, 4, false));
        drop.setPreferredSize(new Dimension(100, 70));

        JLabel label = new JLabel(
                "\u2B07  Drag & drop a file here to compress it, or a .huff file to decompress it",
                SwingConstants.CENTER);
        label.setFont(Theme.FONT_BODY);
        label.setForeground(Theme.TEXT_MUTED);
        drop.add(label, BorderLayout.CENTER);

        drop.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) return false;
                    File f = files.get(0);
                    lastDirectory = f.getParentFile();
                    if (f.getName().toLowerCase().endsWith(".huff")) {
                        pickSaveLocationAndDecompress(f);
                    } else {
                        compressFile(f);
                    }
                    return true;
                } catch (Exception ex) {
                    log("  ERROR reading dropped file: " + ex.getMessage());
                    return false;
                }
            }
        });

        return drop;
    }

    // --------------------------------------------------------------- logging

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setBusy(boolean busy) {
        compressButton.setEnabled(!busy);
        decompressButton.setEnabled(!busy);
    }

    // ------------------------------------------------------------- compress

    private void chooseFileToCompress() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle("Select a file to compress");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File input = chooser.getSelectedFile();
        lastDirectory = input.getParentFile();
        compressFile(input);
    }

    private void compressFile(File input) {
        File output = new File(input.getParentFile(), input.getName() + ".huff");

        setBusy(true);
        progressBar.setValue(0);
        statusLabel.setText("Compressing " + input.getName() + "\u2026");
        log("Compressing: " + input.getAbsolutePath());

        new SwingWorker<CompressionResult, Integer>() {
            @Override
            protected CompressionResult doInBackground() throws Exception {
                HuffmanEncoder encoder = new HuffmanEncoder();
                return encoder.compress(input.getAbsolutePath(), output.getAbsolutePath(), this::publish);
            }

            @Override
            protected void process(List<Integer> chunks) {
                progressBar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    CompressionResult result = get();
                    DecimalFormat df = new DecimalFormat("#,##0");
                    progressBar.setValue(100);
                    statusLabel.setText("Done: " + output.getName());
                    log(String.format("  Original size:   %s bytes", df.format(result.originalSize)));
                    log(String.format("  Compressed size: %s bytes", df.format(result.compressedSize)));
                    log(String.format("  Space saved:     %.2f%%", result.percentSaved()));
                    log("  Saved to: " + output.getAbsolutePath());

                    treePanel.setTree(result.treeRoot, result.frequencies);
                    codeTablePanel.setData(result);
                    tabs.setSelectedIndex(1);

                    JOptionPane.showMessageDialog(HuffmanGUI.this,
                            "Compressed successfully!\n\n" +
                            "Original:   " + df.format(result.originalSize) + " bytes\n" +
                            "Compressed: " + df.format(result.compressedSize) + " bytes\n" +
                            String.format("Saved:      %.2f%%", result.percentSaved()) +
                            "\n\nSaved as:\n" + output.getAbsolutePath(),
                            "Compression Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("Compression failed.");
                    String msg = rootMessage(ex);
                    log("  ERROR: " + msg);
                    JOptionPane.showMessageDialog(HuffmanGUI.this, "Compression failed:\n" + msg,
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------ decompress

    private void chooseFileToDecompress() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle("Select a .huff file to decompress");
        chooser.setFileFilter(new FileNameExtensionFilter("Huffman compressed files (*.huff)", "huff"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File input = chooser.getSelectedFile();
        lastDirectory = input.getParentFile();
        pickSaveLocationAndDecompress(input);
    }

    private void pickSaveLocationAndDecompress(File input) {
        String suggestedName = input.getName().toLowerCase().endsWith(".huff")
                ? input.getName().substring(0, input.getName().length() - 5)
                : input.getName() + ".restored";

        JFileChooser saveChooser = new JFileChooser(lastDirectory);
        saveChooser.setDialogTitle("Choose where to save the restored file");
        saveChooser.setSelectedFile(new File(lastDirectory, suggestedName));
        if (saveChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        decompressFile(input, saveChooser.getSelectedFile());
    }

    private void decompressFile(File input, File output) {
        setBusy(true);
        progressBar.setValue(0);
        statusLabel.setText("Decompressing " + input.getName() + "\u2026");
        log("Decompressing: " + input.getAbsolutePath());

        new SwingWorker<DecompressionResult, Integer>() {
            @Override
            protected DecompressionResult doInBackground() throws Exception {
                HuffmanDecoder decoder = new HuffmanDecoder();
                return decoder.decompress(input.getAbsolutePath(), output.getAbsolutePath(), this::publish);
            }

            @Override
            protected void process(List<Integer> chunks) {
                progressBar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    DecompressionResult result = get();
                    progressBar.setValue(100);
                    statusLabel.setText("Done: " + output.getName());
                    log("  Restored " + result.originalLength + " bytes to: " + output.getAbsolutePath());

                    treePanel.setTree(result.treeRoot, null);
                    codeTablePanel.setData(result);
                    tabs.setSelectedIndex(1);

                    JOptionPane.showMessageDialog(HuffmanGUI.this,
                            "Decompressed successfully!\n\nSaved as:\n" + output.getAbsolutePath(),
                            "Decompression Complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setText("Decompression failed.");
                    String msg = rootMessage(ex);
                    log("  ERROR: " + msg);
                    JOptionPane.showMessageDialog(HuffmanGUI.this, "Decompression failed:\n" + msg,
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ misc

    private static String rootMessage(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    /** Draws a small "H" app icon at runtime instead of shipping an image asset. */
    private static Image buildAppIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.PRIMARY);
        g2.fillRoundRect(2, 2, 60, 60, 18, 18);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 34));
        FontMetrics fm = g2.getFontMetrics();
        String s = "H";
        g2.drawString(s, (64 - fm.stringWidth(s)) / 2, 44);
        g2.dispose();
        return img;
    }

    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to the default look and feel
        }
        SwingUtilities.invokeLater(() -> new HuffmanGUI().setVisible(true));
    }

    public static void main(String[] args) {
        launch();
    }
}

/**
 * Callback used by HuffmanEncoder/HuffmanDecoder to report progress
 * (0-100) back to a caller such as a GUI progress bar, without the
 * core compression logic knowing anything about Swing.
 */
@FunctionalInterface
interface ProgressListener {
    void onProgress(int percentComplete);
}

/**
 * Represents a single node in the Huffman Tree.
 * A leaf node holds an actual byte value (0-255) from the input file.
 * An internal node holds no data (data = -1) and links two child nodes
 * whose combined frequency equals its own.
 */
class HuffmanNode implements Comparable<HuffmanNode> {

    int data;            // byte value (0-255) for leaf nodes, -1 for internal nodes
    long frequency;      // number of occurrences (or combined frequency for internal nodes)
    HuffmanNode left;
    HuffmanNode right;

    public HuffmanNode(int data, long frequency) {
        this.data = data;
        this.frequency = frequency;
        this.left = null;
        this.right = null;
    }

    public HuffmanNode(int data, long frequency, HuffmanNode left, HuffmanNode right) {
        this.data = data;
        this.frequency = frequency;
        this.left = left;
        this.right = right;
    }

    public boolean isLeaf() {
        return left == null && right == null;
    }

    @Override
    public int compareTo(HuffmanNode other) {
        return Long.compare(this.frequency, other.frequency);
    }
}

/**
 * A custom array-based binary Min-Heap (priority queue) of HuffmanNode objects,
 * ordered by frequency. Implemented from scratch (rather than using
 * java.util.PriorityQueue) to demonstrate the underlying data structure
 * used to repeatedly extract the two lowest-frequency nodes while
 * building the Huffman tree.
 */
class MinHeap {

    private HuffmanNode[] heap;
    private int size;
    private int capacity;

    public MinHeap(int capacity) {
        this.capacity = Math.max(capacity, 1);
        this.heap = new HuffmanNode[this.capacity];
        this.size = 0;
    }

    private int parent(int i) { return (i - 1) / 2; }
    private int left(int i)   { return 2 * i + 1; }
    private int right(int i)  { return 2 * i + 2; }

    private void swap(int i, int j) {
        HuffmanNode temp = heap[i];
        heap[i] = heap[j];
        heap[j] = temp;
    }

    private void growIfNeeded() {
        if (size == capacity) {
            capacity *= 2;
            HuffmanNode[] newHeap = new HuffmanNode[capacity];
            System.arraycopy(heap, 0, newHeap, 0, size);
            heap = newHeap;
        }
    }

    /** Inserts a node and restores the heap property by bubbling up. */
    public void insert(HuffmanNode node) {
        growIfNeeded();
        heap[size] = node;
        int i = size;
        size++;

        while (i != 0 && heap[parent(i)].compareTo(heap[i]) > 0) {
            swap(i, parent(i));
            i = parent(i);
        }
    }

    /** Removes and returns the node with the smallest frequency. */
    public HuffmanNode extractMin() {
        if (size <= 0) return null;
        if (size == 1) {
            size--;
            return heap[0];
        }

        HuffmanNode root = heap[0];
        heap[0] = heap[size - 1];
        size--;
        minHeapify(0);
        return root;
    }

    /** Restores the heap property by bubbling down from index i. */
    private void minHeapify(int i) {
        int l = left(i);
        int r = right(i);
        int smallest = i;

        if (l < size && heap[l].compareTo(heap[smallest]) < 0) smallest = l;
        if (r < size && heap[r].compareTo(heap[smallest]) < 0) smallest = r;

        if (smallest != i) {
            swap(i, smallest);
            minHeapify(smallest);
        }
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }
}

/**
 * A hash table built from scratch (no java.util.HashMap) using separate
 * chaining for collision resolution and automatic dynamic resizing once
 * the load factor is exceeded. Used in place of java.util.HashMap for the
 * byte-frequency table and the symbol-to-code table so the project
 * demonstrates hashing, collision handling, and amortized O(1)
 * insert/lookup rather than relying on the built-in collection.
 *
 * @param <K> key type
 * @param <V> value type
 */
class CustomHashMap<K, V> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final double LOAD_FACTOR = 0.75;

    /** Singly linked list node: one bucket slot may chain several of these. */
    private static class Node<K, V> {
        final K key;
        V value;
        Node<K, V> next;

        Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    /** Immutable key/value pair returned by entries(). */
    public static class Entry<K, V> {
        private final K key;
        private final V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() { return key; }
        public V getValue() { return value; }
    }

    private Node<K, V>[] buckets;
    private int size;

    @SuppressWarnings("unchecked")
    public CustomHashMap() {
        buckets = new Node[DEFAULT_CAPACITY];
        size = 0;
    }

    /** Spreads a key's hashCode so high and low bits both influence the bucket index. */
    private int bucketIndex(K key, int capacity) {
        int h = (key == null) ? 0 : key.hashCode();
        h ^= (h >>> 16);
        return (h & 0x7FFFFFFF) % capacity;
    }

    public void put(K key, V value) {
        if (size + 1 > buckets.length * LOAD_FACTOR) {
            resize();
        }
        int idx = bucketIndex(key, buckets.length);
        Node<K, V> node = buckets[idx];
        while (node != null) {
            if (Objects.equals(node.key, key)) {
                node.value = value;
                return;
            }
            node = node.next;
        }
        buckets[idx] = new Node<>(key, value, buckets[idx]);
        size++;
    }

    public V get(K key) {
        int idx = bucketIndex(key, buckets.length);
        Node<K, V> node = buckets[idx];
        while (node != null) {
            if (Objects.equals(node.key, key)) return node.value;
            node = node.next;
        }
        return null;
    }

    public boolean containsKey(K key) {
        int idx = bucketIndex(key, buckets.length);
        Node<K, V> node = buckets[idx];
        while (node != null) {
            if (Objects.equals(node.key, key)) return true;
            node = node.next;
        }
        return false;
    }

    /** Equivalent to java.util.Map#merge: combine with an existing value, or insert if absent. */
    public void merge(K key, V value, BinaryOperator<V> remapper) {
        V existing = get(key);
        put(key, existing == null ? value : remapper.apply(existing, value));
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] old = buckets;
        buckets = new Node[old.length * 2];
        size = 0;
        for (Node<K, V> head : old) {
            Node<K, V> node = head;
            while (node != null) {
                put(node.key, node.value);
                node = node.next;
            }
        }
    }

    public List<Entry<K, V>> entries() {
        List<Entry<K, V>> list = new ArrayList<>(size);
        for (Node<K, V> head : buckets) {
            Node<K, V> node = head;
            while (node != null) {
                list.add(new Entry<>(node.key, node.value));
                node = node.next;
            }
        }
        return list;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    /** Current number of buckets (table capacity) - exposed for the "About" tab's stats. */
    public int capacity() { return buckets.length; }
}

/**
 * Builds the Huffman tree from byte frequencies, derives the prefix code
 * for every byte value, and (de)serializes the tree's shape so a .huff
 * file is fully self-contained (no separate frequency table needed to
 * decompress it).
 *
 * The tree doubles as a binary trie of prefix codes: every root-to-leaf
 * path spells out that leaf's bit-code, and decoding is simply a trie
 * walk one bit at a time.
 */
class HuffmanTree {

    private HuffmanNode root;

    public HuffmanNode getRoot() {
        return root;
    }

    /** Builds the tree using a min-heap: repeatedly merge the two rarest nodes. */
    public HuffmanNode buildTree(CustomHashMap<Integer, Long> frequencies) {
        MinHeap heap = new MinHeap(Math.max(frequencies.size(), 1));

        for (CustomHashMap.Entry<Integer, Long> entry : frequencies.entries()) {
            heap.insert(new HuffmanNode(entry.getKey(), entry.getValue()));
        }

        // Edge case: the file contains only one distinct byte value.
        // The tree is then just that single leaf node (kept as a leaf, not
        // wrapped in a fake parent) so tree (de)serialization stays symmetric.
        if (heap.size() == 1) {
            root = heap.extractMin();
            return root;
        }

        while (heap.size() > 1) {
            HuffmanNode left = heap.extractMin();
            HuffmanNode right = heap.extractMin();
            HuffmanNode parent = new HuffmanNode(-1, left.frequency + right.frequency, left, right);
            heap.insert(parent);
        }

        root = heap.extractMin();
        return root;
    }

    /** Produces a byteValue -> bitstring ("0"/"1") map by walking the tree. */
    public CustomHashMap<Integer, String> generateCodes(HuffmanNode root) {
        CustomHashMap<Integer, String> codes = new CustomHashMap<>();
        if (root == null) return codes;

        if (root.isLeaf()) {
            codes.put(root.data, "0"); // single-symbol file: needs at least 1 bit per symbol
        } else {
            generateCodesHelper(root, "", codes);
        }
        return codes;
    }

    private void generateCodesHelper(HuffmanNode node, String code, CustomHashMap<Integer, String> codes) {
        if (node == null) return;
        if (node.isLeaf()) {
            codes.put(node.data, code);
            return;
        }
        generateCodesHelper(node.left, code + "0", codes);
        generateCodesHelper(node.right, code + "1", codes);
    }

    /**
     * Serializes the tree shape via preorder traversal:
     *   leaf     -> bit '1' followed by the 8-bit byte value
     *   internal -> bit '0' followed by the serialized left then right subtree
     */
    public void writeTree(HuffmanNode node, FileHandler.BitWriter writer) throws IOException {
        if (node.isLeaf()) {
            writer.writeBit(1);
            writer.writeByte(node.data);
        } else {
            writer.writeBit(0);
            writeTree(node.left, writer);
            writeTree(node.right, writer);
        }
    }

    /** Reconstructs the tree shape from the bitstream written by writeTree. */
    public HuffmanNode readTree(FileHandler.BitReader reader) throws IOException {
        int bit = reader.readBit();
        if (bit == 1) {
            int value = reader.readByte();
            return new HuffmanNode(value, 0);
        } else {
            HuffmanNode left = readTree(reader);
            HuffmanNode right = readTree(reader);
            return new HuffmanNode(-1, 0, left, right);
        }
    }
}

/**
 * Handles raw file I/O plus bit-level reading/writing, which is what lets
 * the compressed output pack Huffman codes tightly instead of wasting a
 * whole byte per '0' or '1'.
 */
class FileHandler {

    /** Reads an entire file into a byte array. */
    public static byte[] readFileBytes(String filePath) throws IOException {
        File file = new File(filePath);
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead = 0;
            while (bytesRead < data.length) {
                int result = fis.read(data, bytesRead, data.length - bytesRead);
                if (result == -1) break;
                bytesRead += result;
            }
        }
        return data;
    }

    /** Buffers individual bits and flushes them as full bytes to a stream. */
    public static class BitWriter implements Closeable {
        private final OutputStream out;
        private int currentByte = 0;
        private int numBitsFilled = 0;

        public BitWriter(OutputStream out) {
            this.out = out;
        }

        public void writeBit(int bit) throws IOException {
            currentByte = (currentByte << 1) | (bit & 1);
            numBitsFilled++;
            if (numBitsFilled == 8) {
                out.write(currentByte);
                currentByte = 0;
                numBitsFilled = 0;
            }
        }

        public void writeBits(String bits) throws IOException {
            for (int i = 0; i < bits.length(); i++) {
                writeBit(bits.charAt(i) == '1' ? 1 : 0);
            }
        }

        public void writeByte(int value) throws IOException {
            for (int i = 7; i >= 0; i--) {
                writeBit((value >> i) & 1);
            }
        }

        public void writeInt(int value) throws IOException {
            for (int i = 31; i >= 0; i--) {
                writeBit((value >> i) & 1);
            }
        }

        /** Pads any partial trailing byte with zero bits and writes it out. */
        public void flushRemaining() throws IOException {
            if (numBitsFilled > 0) {
                currentByte = currentByte << (8 - numBitsFilled);
                out.write(currentByte);
                currentByte = 0;
                numBitsFilled = 0;
            }
        }

        @Override
        public void close() throws IOException {
            flushRemaining();
            out.close();
        }
    }

    /** Reads individual bits back out of a stream, one byte at a time under the hood. */
    public static class BitReader implements Closeable {
        private final InputStream in;
        private int currentByte;
        private int numBitsRemaining = 0;

        public BitReader(InputStream in) {
            this.in = in;
        }

        public int readBit() throws IOException {
            if (numBitsRemaining == 0) {
                currentByte = in.read();
                if (currentByte == -1) throw new EOFException("Unexpected end of compressed stream.");
                numBitsRemaining = 8;
            }
            numBitsRemaining--;
            return (currentByte >> numBitsRemaining) & 1;
        }

        public int readByte() throws IOException {
            int value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 1) | readBit();
            }
            return value;
        }

        public int readInt() throws IOException {
            int value = 0;
            for (int i = 0; i < 32; i++) {
                value = (value << 1) | readBit();
            }
            return value;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}

/**
 * Everything the GUI needs after a compression finishes: the size numbers
 * for the stats display, plus the tree and lookup tables so the "Huffman
 * Tree" and "Code Table" tabs can visualize exactly what was built.
 */
class CompressionResult {

    public final long originalSize;
    public final long compressedSize;
    public final HuffmanNode treeRoot;
    public final CustomHashMap<Integer, Long> frequencies;
    public final CustomHashMap<Integer, String> codes;

    public CompressionResult(long originalSize, long compressedSize, HuffmanNode treeRoot,
                              CustomHashMap<Integer, Long> frequencies, CustomHashMap<Integer, String> codes) {
        this.originalSize = originalSize;
        this.compressedSize = compressedSize;
        this.treeRoot = treeRoot;
        this.frequencies = frequencies;
        this.codes = codes;
    }

    public double percentSaved() {
        return 100.0 * (1 - ((double) compressedSize / originalSize));
    }
}

/**
 * Everything the GUI needs after a decompression finishes: the restored
 * length plus the Huffman tree that was read back out of the .huff file's
 * own header, so it can be visualized exactly like a freshly-built one.
 */
class DecompressionResult {

    public final int originalLength;
    public final HuffmanNode treeRoot;

    public DecompressionResult(int originalLength, HuffmanNode treeRoot) {
        this.originalLength = originalLength;
        this.treeRoot = treeRoot;
    }
}

/**
 * Drives the full compression pipeline:
 *   read file -> count frequencies (CustomHashMap) -> build tree (MinHeap)
 *   -> generate codes (CustomHashMap) -> write .huff file
 *
 * Reports progress (0-100) through an optional ProgressListener so a GUI
 * can drive a progress bar without this class knowing Swing exists, and
 * returns a CompressionResult so the GUI can visualize the tree and code
 * table it just built.
 */
class HuffmanEncoder {

    /** 64 KB I/O buffer: without this, BitWriter's per-byte writes each hit
     *  the filesystem directly, which is the single biggest slowdown on
     *  large files (thousands of tiny syscalls instead of a few big ones). */
    private static final int BUFFER_SIZE = 1 << 16;

    public CompressionResult compress(String inputPath, String outputPath) throws IOException {
        return compress(inputPath, outputPath, null);
    }

    public CompressionResult compress(String inputPath, String outputPath, ProgressListener listener) throws IOException {
        byte[] data = FileHandler.readFileBytes(inputPath);

        if (data.length == 0) {
            throw new IOException("Cannot compress an empty file.");
        }

        int total = data.length;

        // Step 1: count byte frequencies with the custom hash table (0-50% of progress)
        CustomHashMap<Integer, Long> frequencies = new CustomHashMap<>();
        for (int i = 0; i < total; i++) {
            int value = data[i] & 0xFF;
            frequencies.merge(value, 1L, Long::sum);
            if (listener != null && (i & 0xFFF) == 0) {
                listener.onProgress((int) (50.0 * i / total));
            }
        }
        report(listener, 50);

        // Step 2: build the Huffman tree with the custom min-heap
        HuffmanTree tree = new HuffmanTree();
        HuffmanNode root = tree.buildTree(frequencies);

        // Step 3: generate prefix codes for every byte value present
        CustomHashMap<Integer, String> codes = tree.generateCodes(root);

        // Step 4: write magic header, original length, tree shape, then encoded bits (50-100%)
        try (FileOutputStream fos = new FileOutputStream(outputPath);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
             FileHandler.BitWriter writer = new FileHandler.BitWriter(bos)) {

            writer.writeByte('H');
            writer.writeByte('U');
            writer.writeByte('F');
            writer.writeByte('F');
            writer.writeInt(data.length);
            tree.writeTree(root, writer);

            for (int i = 0; i < total; i++) {
                int value = data[i] & 0xFF;
                writer.writeBits(codes.get(value));
                if (listener != null && (i & 0xFFF) == 0) {
                    listener.onProgress(50 + (int) (50.0 * i / total));
                }
            }
        }

        report(listener, 100);
        long compressedSize = new File(outputPath).length();
        return new CompressionResult(data.length, compressedSize, root, frequencies, codes);
    }

    private void report(ProgressListener listener, int percent) {
        if (listener != null) listener.onProgress(percent);
    }
}

/**
 * Drives the full decompression pipeline:
 *   read .huff file -> rebuild tree from its header -> walk tree bit-by-bit -> original file
 *
 * Reports progress (0-100) through an optional ProgressListener, and
 * returns a DecompressionResult so the GUI can visualize the tree it just
 * rebuilt straight from the file's own header.
 */
class HuffmanDecoder {

    /** Same reasoning as HuffmanEncoder: buffer both sides so decode -
     *  which writes one output byte at a time - isn't one syscall per byte. */
    private static final int BUFFER_SIZE = 1 << 16;

    public DecompressionResult decompress(String inputPath, String outputPath) throws IOException {
        return decompress(inputPath, outputPath, null);
    }

    public DecompressionResult decompress(String inputPath, String outputPath, ProgressListener listener) throws IOException {
        HuffmanNode root;
        int originalLength;

        try (FileInputStream fis = new FileInputStream(inputPath);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
             FileHandler.BitReader reader = new FileHandler.BitReader(bis);
             FileOutputStream fos = new FileOutputStream(outputPath);
             BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            // Verify magic header "HUFF"
            if (reader.readByte() != 'H' || reader.readByte() != 'U'
                    || reader.readByte() != 'F' || reader.readByte() != 'F') {
                throw new IOException("Not a valid .huff file.");
            }

            originalLength = reader.readInt();

            HuffmanTree tree = new HuffmanTree();
            root = tree.readTree(reader);

            int decoded = 0;

            if (root.isLeaf()) {
                // Whole file was a single repeated byte value; no bits were meaningfully encoded.
                for (int i = 0; i < originalLength; i++) {
                    bos.write(root.data);
                    if (listener != null && (i & 0xFFF) == 0) {
                        listener.onProgress((int) (100.0 * i / originalLength));
                    }
                }
            } else {
                while (decoded < originalLength) {
                    HuffmanNode current = root;
                    while (!current.isLeaf()) {
                        int bit = reader.readBit();
                        current = (bit == 0) ? current.left : current.right;
                    }
                    bos.write(current.data);
                    decoded++;
                    if (listener != null && (decoded & 0xFFF) == 0) {
                        listener.onProgress((int) (100.0 * decoded / originalLength));
                    }
                }
            }
        }

        if (listener != null) listener.onProgress(100);
        return new DecompressionResult(originalLength, root);
    }
}

/**
 * Central color palette and fonts so every panel looks consistent
 * instead of each component picking its own ad-hoc styling.
 */
final class Theme {

    private Theme() { }

    public static final Color BACKGROUND   = new Color(0xF4, 0xF6, 0xFB);
    public static final Color CARD_BG      = Color.WHITE;
    public static final Color PRIMARY      = new Color(0x3F, 0x51, 0xB5); // indigo - compress
    public static final Color PRIMARY_HOVER = new Color(0x5C, 0x6B, 0xC0);
    public static final Color ACCENT       = new Color(0x00, 0x89, 0x7B); // teal - decompress
    public static final Color ACCENT_HOVER = new Color(0x26, 0xA6, 0x9A);
    public static final Color TEXT_DARK    = new Color(0x21, 0x21, 0x21);
    public static final Color TEXT_MUTED   = new Color(0x75, 0x75, 0x75);
    public static final Color BORDER       = new Color(0xE0, 0xE0, 0xE0);
    public static final Color LEAF_NODE    = new Color(0x00, 0x89, 0x7B);
    public static final Color INTERNAL_NODE = new Color(0x3F, 0x51, 0xB5);
    public static final Color EDGE_ZERO    = new Color(0x9E, 0x9E, 0x9E);
    public static final Color EDGE_ONE     = new Color(0xE5, 0x39, 0x35);

    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 22);
    public static final Font FONT_SUBTITLE = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font FONT_BODY = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font("SansSerif", Font.BOLD, 13);
    public static final Font FONT_MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
}

/**
 * A flat, rounded-corner button painted with Graphics2D instead of the
 * default OS chrome, with a hover highlight. Small piece of custom Swing
 * work that makes the UI look intentional rather than default-Swing gray.
 */
class RoundedButton extends JButton {

    private final Color baseColor;
    private final Color hoverColor;
    private boolean hovering = false;

    public RoundedButton(String text, Color baseColor, Color hoverColor) {
        super(text);
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;

        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(Theme.FONT_BODY_BOLD.deriveFont(15f));
        setHorizontalAlignment(SwingConstants.CENTER);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = !isEnabled() ? new Color(0xBD, 0xBD, 0xBD) : (hovering ? hoverColor : baseColor);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g2.dispose();

        super.paintComponent(g);
    }
}

/**
 * Renders the Huffman tree as a diagram.
 *
 * Layout algorithm: a post-order walk assigns each leaf the next free
 * horizontal slot (left to right, in-order), then each internal node is
 * placed at the horizontal midpoint of its two children. Depth in the
 * tree maps directly to the vertical row. This is the standard
 * "leaves-first" recursive layout used for drawing binary trees, and its
 * cost is O(n) in the number of nodes, matching the tree's own build cost
 * profile.
 */
class TreePanel extends JPanel {

    private static final int NODE_RADIUS = 20;
    private static final int H_GAP = 64;
    private static final int V_GAP = 80;
    private static final int MARGIN = 40;

    private HuffmanNode root;
    private CustomHashMap<Integer, Long> frequencies; // optional, null after a decompress
    private final Map<HuffmanNode, Point> positions = new IdentityHashMap<>();
    private int leafCounter;
    private int maxDepth;

    public TreePanel() {
        setBackground(Theme.CARD_BG);
    }

    public void setTree(HuffmanNode root, CustomHashMap<Integer, Long> frequencies) {
        this.root = root;
        this.frequencies = frequencies;
        computeLayout();

        int width = Math.max(leafCounter, 1) * H_GAP + MARGIN * 2;
        int height = (maxDepth + 1) * V_GAP + MARGIN * 2;
        setPreferredSize(new Dimension(width, height));
        revalidate();
        repaint();
    }

    private void computeLayout() {
        positions.clear();
        leafCounter = 0;
        maxDepth = 0;
        if (root != null) assignPositions(root, 0);
    }

    private void assignPositions(HuffmanNode node, int depth) {
        if (node == null) return;
        maxDepth = Math.max(maxDepth, depth);

        if (node.isLeaf()) {
            int x = MARGIN + leafCounter * H_GAP;
            int y = MARGIN + depth * V_GAP;
            positions.put(node, new Point(x, y));
            leafCounter++;
        } else {
            assignPositions(node.left, depth + 1);
            assignPositions(node.right, depth + 1);
            Point l = positions.get(node.left);
            Point r = positions.get(node.right);
            int x = (r != null) ? (l.x + r.x) / 2 : l.x;
            int y = MARGIN + depth * V_GAP;
            positions.put(node, new Point(x, y));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (root == null) {
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(Theme.FONT_BODY);
            g2.drawString("Compress or decompress a file to see its Huffman tree here.", 24, 30);
            g2.dispose();
            return;
        }

        drawEdges(g2, root);
        drawNodes(g2, root);
        g2.dispose();
    }

    private void drawEdges(Graphics2D g2, HuffmanNode node) {
        if (node == null || node.isLeaf()) return;
        Point p = positions.get(node);

        if (node.left != null) {
            Point l = positions.get(node.left);
            g2.setColor(Theme.EDGE_ZERO);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(p.x, p.y, l.x, l.y);
            drawEdgeLabel(g2, p, l, "0", Theme.EDGE_ZERO);
            drawEdges(g2, node.left);
        }
        if (node.right != null) {
            Point r = positions.get(node.right);
            g2.setColor(Theme.EDGE_ONE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(p.x, p.y, r.x, r.y);
            drawEdgeLabel(g2, p, r, "1", Theme.EDGE_ONE);
            drawEdges(g2, node.right);
        }
    }

    private void drawEdgeLabel(Graphics2D g2, Point from, Point to, String label, Color color) {
        int mx = (from.x + to.x) / 2;
        int my = (from.y + to.y) / 2;
        g2.setColor(Color.WHITE);
        g2.fillOval(mx - 8, my - 8, 16, 16);
        g2.setColor(color);
        g2.setFont(Theme.FONT_BODY_BOLD);
        g2.drawString(label, mx - 4, my + 5);
    }

    private void drawNodes(Graphics2D g2, HuffmanNode node) {
        if (node == null) return;
        Point p = positions.get(node);

        if (node.isLeaf()) {
            g2.setColor(Theme.LEAF_NODE);
            g2.fillOval(p.x - NODE_RADIUS, p.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.FONT_BODY_BOLD.deriveFont(11f));
            String label = symbolLabel(node.data);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, p.x - fm.stringWidth(label) / 2, p.y + 4);

            Long freq = (frequencies != null) ? frequencies.get(node.data) : null;
            if (freq != null) {
                g2.setColor(Theme.TEXT_MUTED);
                g2.setFont(Theme.FONT_BODY.deriveFont(11f));
                String freqLabel = "x" + freq;
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(freqLabel, p.x - fm2.stringWidth(freqLabel) / 2, p.y + NODE_RADIUS + 14);
            }
        } else {
            g2.setColor(Theme.INTERNAL_NODE);
            g2.fillOval(p.x - NODE_RADIUS, p.y - NODE_RADIUS, NODE_RADIUS * 2, NODE_RADIUS * 2);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.FONT_BODY.deriveFont(10f));
            String label = String.valueOf(node.frequency);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, p.x - fm.stringWidth(label) / 2, p.y + 4);
            drawNodes(g2, node.left);
            drawNodes(g2, node.right);
        }
    }

    /** Printable ASCII shows as 'x'; everything else shows as a hex byte. */
    private static String symbolLabel(int value) {
        if (value >= 32 && value <= 126) {
            return "'" + (char) value + "'";
        }
        return String.format("%02X", value);
    }
}

/**
 * Shows every symbol's frequency and assigned Huffman code as a sortable
 * table. It opens sorted by <b>Frequency, descending</b> by default (falling
 * back to Bits, ascending, when frequency data isn't available) so the
 * Huffman property is visible at a glance: the most common bytes end up
 * with the shortest codes, and rarer bytes get longer ones. Any column
 * header can still be clicked to re-sort interactively.
 */
class CodeTablePanel extends JPanel {

    private static final int COL_SYMBOL = 0;
    private static final int COL_HEX = 1;
    private static final int COL_FREQUENCY = 2;
    private static final int COL_CODE = 3;
    private static final int COL_BITS = 4;

    private final DefaultTableModel model;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JLabel emptyLabel;

    public CodeTablePanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.CARD_BG);

        model = new DefaultTableModel(new Object[]{"Symbol", "Hex", "Frequency", "Code", "Bits"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return (col == COL_FREQUENCY || col == COL_BITS) ? Integer.class : String.class;
            }
        };
        table = new JTable(model);
        table.setRowHeight(24);
        table.setFont(Theme.FONT_MONO);
        table.getTableHeader().setFont(Theme.FONT_BODY_BOLD);

        sorter = new TableRowSorter<>(model);
        // Frequency can be null (after a decompress, where original counts
        // aren't stored) - the default comparator would throw on null vs
        // null, so treat null as "lowest" instead of letting it blow up.
        sorter.setComparator(COL_FREQUENCY, Comparator.comparing(
                v -> (Integer) v, Comparator.nullsFirst(Comparator.naturalOrder())));
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);

        DefaultTableCellRenderer rightAlign = new DefaultTableCellRenderer();
        rightAlign.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(COL_FREQUENCY).setCellRenderer(rightAlign);
        table.getColumnModel().getColumn(COL_BITS).setCellRenderer(rightAlign);

        emptyLabel = new JLabel("Compress or decompress a file to see its code table here.", SwingConstants.CENTER);
        emptyLabel.setForeground(Theme.TEXT_MUTED);
        emptyLabel.setFont(Theme.FONT_BODY);

        add(emptyLabel, BorderLayout.CENTER);
    }

    public void setData(CompressionResult result) {
        populate(result.codes, result.frequencies);
    }

    public void setData(DecompressionResult result) {
        CustomHashMap<Integer, String> codes = new HuffmanTree().generateCodes(result.treeRoot);
        populate(codes, null);
    }

    private void populate(CustomHashMap<Integer, String> codes, CustomHashMap<Integer, Long> frequencies) {
        model.setRowCount(0);

        List<CustomHashMap.Entry<Integer, String>> entries = new ArrayList<>(codes.entries());

        if (frequencies != null) {
            // Most frequent first; ties broken by shorter code, then by symbol
            // value, so the row order is fully deterministic either way.
            entries.sort(Comparator
                    .comparingLong((CustomHashMap.Entry<Integer, String> e) -> frequencies.get(e.getKey())).reversed()
                    .thenComparingInt(e -> e.getValue().length())
                    .thenComparingInt(CustomHashMap.Entry::getKey));
        } else {
            // No frequency data to sort by (tree came from a .huff file's
            // header alone) - shortest code first is the next most useful view.
            entries.sort(Comparator
                    .comparingInt((CustomHashMap.Entry<Integer, String> e) -> e.getValue().length())
                    .thenComparingInt(CustomHashMap.Entry::getKey));
        }

        for (CustomHashMap.Entry<Integer, String> e : entries) {
            int value = e.getKey();
            String code = e.getValue();
            Long freq = (frequencies != null) ? frequencies.get(value) : null;

            model.addRow(new Object[]{
                    symbolLabel(value),
                    String.format("0x%02X", value),
                    freq != null ? freq.intValue() : null,
                    code,
                    code.length()
            });
        }

        // Rows are already in the right order; clear any leftover sort
        // arrow from a previous file so the header reflects reality, then
        // (re)apply the default sort so re-sorting by clicking still works
        // sensibly starting from this state.
        List<RowSorter.SortKey> defaultKeys = new ArrayList<>();
        if (frequencies != null) {
            defaultKeys.add(new RowSorter.SortKey(COL_FREQUENCY, SortOrder.DESCENDING));
        } else {
            defaultKeys.add(new RowSorter.SortKey(COL_BITS, SortOrder.ASCENDING));
        }
        sorter.setSortKeys(defaultKeys);

        removeAll();
        add(new JScrollPane(table), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static String symbolLabel(int value) {
        if (value == ' ') return "SPACE";
        if (value == '\n') return "\\n";
        if (value == '\r') return "\\r";
        if (value == '\t') return "\\t";
        if (value >= 32 && value <= 126) return String.valueOf((char) value);
        return "(non-printable)";
    }
}

/**
 * A static reference tab for the Advanced Data Structures write-up:
 * which structures the project uses, where each one lives in the code,
 * and why it was the right choice there. Kept in-app so it doubles as
 * demo/grading material.
 */
class AboutPanel extends JPanel {

    public AboutPanel() {
        setLayout(new BorderLayout());
        setBackground(Theme.CARD_BG);

        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setText(HTML);
        pane.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    private static final String HTML =
        "<html><body style='font-family:SansSerif; font-size:10.5pt; margin:18px; color:#212121;'>" +
        "<h2 style='color:#3F51B5;'>Data Structures Used in This Project</h2>" +

        "<h3 style='color:#00897B;'>1. Min-Heap / Priority Queue &mdash; <code>MinHeap</code> class</h3>" +
        "<p>A binary min-heap built from scratch on a resizable array (no <code>java.util.PriorityQueue</code>). " +
        "Supports <b>insert</b> (bubble-up) and <b>extractMin</b> (swap-with-last + bubble-down), both " +
        "<b>O(log n)</b>. Used in <code>HuffmanTree.buildTree()</code> to repeatedly pull out the two " +
        "lowest-frequency nodes and merge them &mdash; the classic greedy step that makes Huffman coding " +
        "optimal. Building the whole tree this way costs <b>O(n log n)</b> for n distinct byte values " +
        "(n &le; 256 here, so effectively constant in practice, but the algorithm is written generally).</p>" +

        "<h3 style='color:#00897B;'>2. Custom Hash Table (Separate Chaining) &mdash; <code>CustomHashMap</code> class</h3>" +
        "<p>A generic hash map implemented from scratch instead of using <code>java.util.HashMap</code>, so the " +
        "hashing mechanics are visible rather than hidden inside the JDK. Collisions are resolved with " +
        "<b>separate chaining</b> (each bucket is a singly linked list), the hash is spread with an " +
        "XOR-fold on the high bits before masking into the bucket range, and the table automatically " +
        "<b>doubles in size and rehashes</b> every entry once the load factor passes 0.75. " +
        "Average-case <b>put/get is O(1)</b>; worst case (all keys collide) degrades to O(n) as with " +
        "any chained hash table. Used in two places: " +
        "<code>HuffmanEncoder</code> (byte value &rarr; frequency count while scanning the file) and " +
        "<code>HuffmanTree.generateCodes()</code> (byte value &rarr; bit-string code).</p>" +

        "<h3 style='color:#00897B;'>3. Binary Tree / Binary Trie &mdash; <code>HuffmanNode</code>, <code>HuffmanTree</code> classes</h3>" +
        "<p>The Huffman tree is a full binary tree (every internal node has exactly two children) built " +
        "bottom-up from the min-heap. It plays a dual role: as a <b>binary trie of prefix codes</b>, where " +
        "every root-to-leaf path spells out that byte's code (left = 0, right = 1) and no code is a prefix " +
        "of another; and as the <b>decode structure</b> itself &mdash; decompression is just a trie walk, " +
        "one input bit at a time, until a leaf is reached. Code generation is a single <b>O(n)</b> DFS over " +
        "the tree. The tree is also serialized directly into the <code>.huff</code> file header (preorder " +
        "traversal) so a compressed file is fully self-contained and needs no external frequency table to decode.</p>" +

        "<h3 style='color:#00897B;'>4. Bit-Level Stream Buffers &mdash; <code>FileHandler.BitWriter</code> / <code>BitReader</code></h3>" +
        "<p>Huffman codes are variable-length bit strings, but disks only deal in whole bytes. " +
        "<code>BitWriter</code> packs individual bits into a byte accumulator and flushes full bytes to the " +
        "stream (padding the final byte with zeros); <code>BitReader</code> unpacks a byte into 8 bits on " +
        "demand. This is what makes the output an actually compact bitstream instead of one ASCII '0'/'1' " +
        "character per bit &mdash; the difference between real compression and a simulation of it.</p>" +

        "<h3 style='color:#00897B;'>5. Recursive Tree-Layout Algorithm &mdash; <code>TreePanel</code> class</h3>" +
        "<p>The \"Huffman Tree\" tab lays the tree out with the standard leaves-first recursive drawing " +
        "algorithm: a post-order walk hands each <b>leaf</b> the next free horizontal slot left-to-right, " +
        "then every <b>internal node</b> is centered above the midpoint of its two children, with tree " +
        "depth mapped directly to vertical row. This is an <b>O(n)</b> tree traversal that turns the " +
        "abstract structure into a readable diagram, and it's the same idea used by most tree-drawing " +
        "and org-chart layout tools.</p>" +

        "<h2 style='color:#3F51B5;'>Where Each Structure Is Used, End to End</h2>" +
        "<table border='0' cellpadding='6' style='border-collapse:collapse; width:100%;'>" +
        "<tr style='background:#EDEFF9;'><th align='left'>Stage</th><th align='left'>Structure</th><th align='left'>Purpose</th></tr>" +
        "<tr><td>Read file</td><td>byte[]</td><td>Raw input, works on any file type, not just text</td></tr>" +
        "<tr style='background:#FAFAFA;'><td>Count frequencies</td><td><b>Custom Hash Table</b></td><td>byte value &rarr; occurrence count, amortized O(1) per byte</td></tr>" +
        "<tr><td>Build tree</td><td><b>Min-Heap</b></td><td>Always merge the two rarest nodes first (greedy optimal substructure)</td></tr>" +
        "<tr style='background:#FAFAFA;'><td>Generate codes</td><td><b>Binary Trie / Hash Table</b></td><td>DFS the tree, store byte &rarr; bit-string in the hash table</td></tr>" +
        "<tr><td>Write file</td><td><b>Bit Buffer</b></td><td>Pack variable-length codes into real bytes on disk</td></tr>" +
        "<tr style='background:#FAFAFA;'><td>Decompress</td><td><b>Binary Trie</b></td><td>Walk the tree bit-by-bit until a leaf gives back the original byte</td></tr>" +
        "<tr><td>Visualize</td><td><b>Recursive Layout</b></td><td>Turn the tree object graph into an on-screen diagram</td></tr>" +
        "</table>" +

        "<h2 style='color:#3F51B5;'>Complexity Summary</h2>" +
        "<ul>" +
        "<li>Let n = file size in bytes, k = number of distinct byte values (k &le; 256).</li>" +
        "<li>Frequency counting: <b>O(n)</b> amortized, via the custom hash table.</li>" +
        "<li>Tree construction: <b>O(k log k)</b>, via the min-heap (k-1 extract/insert pairs).</li>" +
        "<li>Code generation: <b>O(k)</b>, a single DFS over the tree (at most k leaves, k-1 internal nodes).</li>" +
        "<li>Encoding: <b>O(n)</b>, one hash lookup and bit-write per input byte.</li>" +
        "<li>Decoding: <b>O(n &middot; h)</b> worst case, where h is tree height (h &le; k); in practice close " +
        "to O(n) since real files are skewed toward a few short codes.</li>" +
        "<li>Overall: <b>O(n + k log k)</b>, i.e. linear in file size for anything but pathologically many distinct byte values.</li>" +
        "</ul>" +

        "</body></html>";
}
