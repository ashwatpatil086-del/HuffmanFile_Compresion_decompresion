# Huffman File Compression System — Single-File GUI Edition

Everything — the full Swing GUI, the Huffman tree visualization, the
sortable code table, the custom min-heap and hash table, all of it — lives
in one file: `HuffmanGUI.java`. See the comment banner at the top of that
file for a section-by-section map of what's where.

## How to run

**Option A — one command, no compile step (Java 11+):**
```bash
java HuffmanGUI.java
```

**Option B — classic two-step:**
```bash
javac HuffmanGUI.java
java HuffmanGUI
```

**Option C — VS Code:**
1. Open this folder in VS Code.
2. Install the **"Extension Pack for Java"** if you don't have it.
3. Open `HuffmanGUI.java` and click the **▶ Run** button above
   `public class HuffmanGUI`.

## Requirements

- JDK 11 or newer (`java -version` to check).
- A graphical display — it's a desktop window, not a console app, so a
  plain headless SSH session won't show it (use `ssh -X` or VNC if remote).

## Using the app

- **Compress**: click "Compress File…" or drag any file onto the drop
  zone. Saves `<filename>.huff` next to the original.
- **Decompress**: click "Decompress .huff File…" (or drag a `.huff` file
  onto the drop zone), then choose where to save the restored file.
- After either action it auto-switches to the **Huffman Tree** tab.
- **Code Table** tab: every symbol's frequency and bit-code, sorted by
  frequency so you can see the Huffman property at a glance.
- **Data Structures Used** tab: the write-up on which structures are used
  where (Min-Heap, custom Hash Table, Binary Tree/Trie, bit-level I/O
  buffers, the tree-layout algorithm).
