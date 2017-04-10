import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

class BTree {

    BTreeNode root;
    static int t; // degree
    RandomAccessFile file;
    long rpos; // position of root node in file
    long num_nodes;
    static long next_address; // next available address
    static long NODE_BYTES;

    public BTree(int k, int t, String filename) {
        int mode = 0;  // 0 create file, 1 file already exists
        this.t = t;
        long T = (long)t;
        NODE_BYTES = 2L +            // char leaf = 2 bytes
                     2L +            // char root = 2 bytes
                     4L +            // int n = number of keys currently stored in node
                     12L*(2L*T-1L) + // array of keys = 8bytes*(2t-1)
                     8L*(2L*T);      // array of children pointers = 8bytes*(2t)

        try {
            file = new RandomAccessFile(filename, "rw");
        } catch (FileNotFoundException ex) {
            System.err.println("Can't open btree file " + filename + ". Exiting");
            System.exit(1);
        }
        try {
            // write metadata node first to set rpos
            mode = init_metadata(filename, file, k, t);
        } catch (IOException ex) {
            System.err.println("Can't write metadata to btree file " + filename + ". Exiting");
            System.exit(1);
        }

        next_address = rpos;

        if(mode == 0) {
            root = new BTreeNode();
            root.setLeaf(true);
            root.setRoot(true);
            disk_write(root);
        }
        else {
            root = disk_read(rpos); // read the root node from disk
        }
    }

    public class Key implements Comparable<Key>{
        public Long key;
        public Integer freq;

        public Key() {
            key = 0L;
            freq = -9;
        }

        @Override
        public int compareTo(Key other){
            return key.compareTo(other.key);
        }
    }

    public class BTreeNode {
        char leaf;
        char root;
        int n;
        Key[] keys;
        Long[] children; // file byte offset of children

        long address;
        public int search_idx;

        public BTreeNode() {
            n = 0;
            keys = new Key[2*t-1];
            for(int i = 0; i < 2*t-1; i++) {
                keys[i] = new Key();
            }
            children = new Long[2*t];
            for (int i = 0; i < 2*t; i++) {
                children[i] = -1L;
            }
            search_idx = -1;
            address = next_address();
            root = 'N';
        }

        public void setN(int n) {
            this.n = n;
        }

        public int getN() {
            return n;
        }

        public void setLeaf(boolean set) {
            if (set) {
                leaf = 'L';
            } else {
                leaf = 'I';
            }
        }

        public boolean isLeaf() {
            return leaf == 'L';
        }

        public void setRoot(boolean set) {
            if (set) {
                root = 'R';
            } else {
                root = 'N';
            }
        }

        public boolean isRoot() {
            return root == 'R';
        }

        public void setKeys(Key[] keys) {
            System.arraycopy(keys, 0, this.keys, 0, keys.length);
        }

        public Key[] getKeys() {
            Key[] gkeys = new Key[this.n];
            System.arraycopy(this.keys, 0, gkeys, 0, n);
            return keys;
        }

        public void setChildren(Long[] children) {
            System.arraycopy(children, 0, this.children, 0, children.length);
        }

        public Long[] getChildren() {
            Long[] gchildren = new Long[this.n + 1];
            System.arraycopy(this.children, 0, gchildren, 0, n);
            return children;
        }

        public int nchildren() {
            int count = 0;
            for(int i = 0; i < children.length; i++) {
                if(children[i] != -1L)
                    count++;
            }
            return count;
        }
    }

    public void printr(BTreeNode r, int sp) {

        String ss = "";
        for (int s = 0; s < sp; s++) {
            ss = ss.concat(" ");
        }

        System.err.printf("%s(address = %d, root = %c, leaf = %c, n = %d, nc = %d) |", ss, r.address, r.root, r.leaf, r.n, r.nchildren());
        for (int i = 0; i < r.n-1; i++) {
            System.err.printf("%d,", r.keys[i].key);
        }
        System.err.printf("%d", r.keys[r.n-1].key);
        System.err.print("| {");
        for(Long children : r.children) {
            System.err.printf("%d,", children);
        }
        System.err.println("}");
        for (Long children : r.children) {
            if (children != -1) {
                printr(disk_read(children), sp + 4);
            }
        }

    }

    public void print() {
        System.err.println("number of nodes = " + num_nodes);
        if(num_nodes > 0)
            printr(root, 0);
    }

    public long next_address() {
        long adr = next_address;
        next_address += NODE_BYTES;
        return adr;
    }

    public void address_rollback() {
        next_address -= NODE_BYTES;
    }

    private int init_metadata(String filename, RandomAccessFile file, int k, int t) throws IOException {
        long META_BYTES = 20L;

        int mode = 0;

        // check if file exists
        if (file.length() > 4) {
            System.err.println("Reading block file " + filename + ".  k = " + k + ", t = " + t);
            file.seek(0L);
            if (file.readInt() == 1234) {
                // check k
                fseek(4);
                int kr = file.readInt();
                if(kr != k) {
                    System.err.println("k in file is wrong = " + kr);
                    System.exit(1);
                }

                // check t
                fseek(8);
                int tr = file.readInt();
                if(tr != t) {
                    System.err.println("tin file is wrong = " + tr);
                    System.exit(1);
                }

                // read fpos
                fseek(12L);
                long rrpos = file.readLong();
                this.rpos = rrpos;

                // set num_nodes
                long fsize = file.length();
                long node_bytes = fsize - META_BYTES;
                long rnum_nodes = node_bytes/BTree.NODE_BYTES;
                this.num_nodes = rnum_nodes;

                mode = 1;
            }
        } else {
            System.err.println("creating block file " + filename + ".  k = " + k + ", t = " + t);
            file.setLength(0L); // clear out any contents if this file is existing

            // field 0:  password = 1234, 4 bytes.  used to signify file is valid.
            file.seek(0);
            file.writeInt(1234);

            // field 1:  k, sequence_length, 4 bytes
            file.seek(4);
            file.writeInt(k);

            // field 2:  t, 4 bytes
            file.seek(8);
            file.writeInt(t);

            // field 3:  ptr to root node, 8 bytes
            file.seek(12);
            file.writeLong(META_BYTES);

            this.rpos = META_BYTES;
            this.num_nodes = 0;

            mode = 0;
        }
        return mode;
    }

    private void update_rpos() {
        fseek(12);
        try {
            file.writeLong(this.root.address);
        } catch (IOException ex) {
            System.err.println("Can't access file " + file);
            System.exit(1);
        }
    }

    public void fseek(long i) {
        try {
            file.seek(i);
        } catch (IOException ex) {
            System.err.println("Can't access file " + file);
            System.exit(1);
        }
    }

    public BTreeNode search(Long key) {
        Key k = new Key();
        k.key = key;
        return rsearch(this.root, k);
    }

    public BTreeNode rsearch(BTreeNode x, Key key) {
        BTreeNode result;
        boolean found = false;
        int i = 0;
        if(key.compareTo(x.keys[i]) == 0)
            found = true;
        else {
            while (i < x.n && key.compareTo(x.keys[i]) > 0) {
                i++;
            }
        }
        if(i < x.n && key.compareTo(x.keys[i]) == 0) {
            x.search_idx = i;
            return x;
        } else if (x.isLeaf()) {
            result = null;
        } else {
            if (x.children[i] != -1L) {
                BTreeNode c = disk_read(x.children[i]);
                result = rsearch(c, key);
            }
            else {
                result = null;
                System.err.println("null child encountered during search!");
                System.exit(1);
            }
        }
        return result;
    }

    private void split_child(BTreeNode x, int i) {
        BTreeNode z = new BTreeNode();
        BTreeNode y = disk_read(x.children[i]);
        z.setLeaf(y.isLeaf());
        z.setN(t - 1);
        for (int j = 0; j < t - 1; j++) {
            z.keys[j] = y.keys[j + t];
        }
        if (!y.isLeaf()) {
            for (int j = 0; j < t; j++) {
                z.children[j] = y.children[j + t];
                y.children[j + t] = -1L;
            }
        }
        y.setN(t - 1);
        for (int j = x.getN(); j >= i + 1; j--) {
            x.children[j + 1] = x.children[j];
        }
        x.children[i + 1] = z.address;
        for (int j = x.getN() - 1; j >= i; j--) {
            x.keys[j + 1] = x.keys[j];
        }
        x.keys[i] = y.keys[t - 1];
        x.setN(x.getN() + 1);
        disk_write(y);
        disk_write(z);
        disk_write(x);
    }

    public void insert(Long key) {

        BTreeNode found = search(key);

        if(found != null) {
            found.keys[found.search_idx].freq = found.keys[found.search_idx].freq + 1;
            disk_write(found);
        }
        else {
            Key k = new Key();
            k.key = key;
            k.freq = 1;

            BTreeNode r = this.root;
            if (r.getN() == 2*t-1) {
                BTreeNode s = new BTreeNode();
                r.setRoot(false);
                disk_write(r); // save root change
                s.setRoot(true);
                this.root = s;
                this.rpos = s.address;
                s.setLeaf(false);
                s.setN(0);
                s.children[0] = r.address;
                split_child(s, 0);
                insert_nonfull(s, k);
                update_rpos();
            } else {
                insert_nonfull(r, k);
            }
            num_nodes++;
        }
    }

    public void insert_nonfull(BTreeNode x, Key key) {
        int i = x.n - 1;
        if (x.isLeaf()) {
            while (i >= 0 && key.compareTo(x.keys[i]) < 1) {
                x.keys[i+1] = x.keys[i];
                i--;
            }
            x.keys[i + 1] = key;
            x.n++;
            disk_write(x);
        } else {
            while (i >= 0 && key.compareTo(x.keys[i]) < 1) {
                i--;
            }
            i++;
            BTreeNode xci = disk_read(x.children[i]);
            if (xci.n == 2*t-1) {
                split_child(x, i);
                if (key.compareTo(x.keys[i]) > 1) {
                    i++;
                }
            }
            xci = disk_read(x.children[i]);
            insert_nonfull(xci, key);
        }
    }

    private BTreeNode disk_read(Long address) {

        BTreeNode x = new BTreeNode();
        // the address on disk is the real address
        // set the address of this node to disk address
        // roll back the BTree next_address counter
        x.address = address;
        address_rollback();

        try {
            // address + 0:  leaf
            fseek(address);
            x.setLeaf(file.readChar() == 'L');

            // address + 2:  root
            x.setRoot(file.readChar() == 'R');

            // address + 4:  n, subseq length
            int rn = file.readInt();
            x.setN(rn);

            // address + 8:  keys
            Key[] rkeys = new Key[rn];
            for(int j = 0; j < rn; j++) {
                rkeys[j] = new Key();
            }
            for (int j = 0; j < rn; j++) { // only read n keys
                rkeys[j].key = file.readLong();
                rkeys[j].freq = file.readInt();
            }
            x.setKeys(rkeys);

            // address + 8 + 8*(2t-1) + 4*(2t-1):  children pointers
            long NKEYS = (2L*(long)t)-1L;
            int NCHILDREN = 2*t;
            Long[] rchildren = new Long[NCHILDREN];
            long jdx = address + 8L + (8L + 4L)*NKEYS;
            fseek(jdx);
            for (int j = 0; j < NCHILDREN; j++) {
                rchildren[j] = file.readLong();
            }
            x.setChildren(rchildren);
        } catch (IOException ex) {
            System.err.println("IOException in disk_read.  Exiting.");
            System.exit(1);
        }
        return x;
    }

    private void disk_write(BTreeNode x) {
        try {
            long idx = x.address;

            // address + 0:  leaf
            fseek(idx);
            file.writeChar(x.leaf);

            // address + 2:  root
            file.writeChar(x.root);

            // address + 4:  n
            file.writeInt(x.n);

            // address + 8:  keys
            int NKEYS = 2*t-1;
            for (int i = 0; i < NKEYS; i++) {
                if (i < x.n) {
                    file.writeLong(x.keys[i].key);
                    file.writeInt(x.keys[i].freq);
                } else {
                    file.writeLong(0xadde_eeee_adde_aaaaL);
                    file.writeInt(0xdead_beef);
                }
            }

            // address + 8 + NKEYS*NODE_SIZE
            int NCHILDREN = 2*t;
            for (int i = 0; i < NCHILDREN; i++) {
                file.writeLong(x.children[i]);
            }
        } catch (IOException ex) {
            System.err.println("IOException in disk_write.  Exiting.");
            System.exit(1);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // test methods used for debug
    ////////////////////////////////////////////////////////////////////////////
    public void test_split() {
        // x = nonfull internal node
        BTreeNode x = new BTreeNode();
        x.n = 2;
        x.leaf = 'I';
        x.keys[0].key = 4L;
        x.keys[1].key = 10L;
        disk_write(x);

        // y = full child of x
        BTreeNode y = new BTreeNode();
        y.n = 3;
        y.leaf = 'I';
        y.keys[0].key = 2L;
        y.keys[1].key = 7L;
        y.keys[2].key = 11L;
        disk_write(y);

        // dummy children for y
        BTreeNode w = new BTreeNode();
        w.n = 1;
        w.leaf = 'L';
        w.keys[0].key = 1L;
        disk_write(w);

        BTreeNode v = new BTreeNode();
        v.n = 1;
        v.leaf = 'L';
        v.keys[0].key = 3L;
        disk_write(v);

        y.children[0] = w.address;
        y.children[1] = v.address;
        disk_write(y);

        x.children[0] = y.address;
        disk_write(x);

        printr(x, 0);
        System.err.println("----------------");

        split_child(x, 0);
        printr(x, 0);
    }

    public void test_file() {
        BTreeNode x = new BTreeNode();

        x.keys[0].key = 1L;
        x.keys[1].key = 2L;
        x.keys[2].key = 3L;
        x.n = 3;
        x.leaf = 'I';

        BTreeNode c0 = new BTreeNode();
        c0.leaf = 'L';
        c0.n = 3;
        c0.keys[0].key = 4L;
        c0.keys[1].key = 5L;
        c0.keys[2].key = 6L;
        BTreeNode c1 = new BTreeNode();
        c1.leaf = 'L';
        c1.n = 3;
        c1.keys[0].key = 7L;
        c1.keys[1].key = 8L;
        c1.keys[2].key = 9L;
        BTreeNode c2 = new BTreeNode();
        c2.leaf = 'L';
        c2.n = 3;
        c2.keys[0].key = 10L;
        c2.keys[1].key = 11L;
        c2.keys[2].key = 12L;
        BTreeNode c3 = new BTreeNode();
        c3.leaf = 'L';
        c3.n = 3;
        c3.keys[0].key = 13L;
        c3.keys[1].key = 14L;
        c3.keys[2].key = 15L;

        disk_write(c0);
        disk_write(c1);
        disk_write(c2);
        disk_write(c3);

        x.children[0] = c0.address;
        x.children[1] = c1.address;
        x.children[2] = c2.address;
        x.children[3] = c3.address;
        disk_write(x);

        x = disk_read(x.address);
        c0 = disk_read(c0.address);
        c1 = disk_read(c1.address);
        c2 = disk_read(c2.address);
        c3 = disk_read(c3.address);

        disk_write(c0);
        disk_write(c1);
        disk_write(c2);
        disk_write(c3);

        printr(x, 0);
    }

    public void check_valid_r(BTreeNode x) {
        System.err.println("checking BTreeNode at address = " + x.address);
        // check number of keys
        if(!x.isRoot()) {
            if(x.n < t-1 || x.n > 2*t-1)
                System.err.println("number of keys violation address = " + x.address);
            if(!x.isLeaf() && x.nchildren() < t)
                System.err.println("internal node must have at least t = " + t + " children, found " + x.n + " address = " + x.address);
            if(!x.isLeaf() && x.nchildren() > 2*t)
                System.err.println("internal node has too many children " + x.nchildren() + " address = " + x.address);
        }
        else if(x.n < 1) {
            System.err.println("tree is empty, n=0 for root node address = " + x.address);
        }
        // leaf node must not have any children
        if (x.isLeaf()) {
            for (int j = 0; j < x.children.length; j++) {
                if (x.children[j] != -1L) {
                    System.err.println("leaf node has children address = " + x.address + " child " + j);
                }
            }
        }
        for(int i = 1; i < x.n; i++) {
            // check that keys are in non-decreasing order
            if(x.keys[i].compareTo(x.keys[i-1]) < 1)
                System.err.println("bad key order found node address = " + x.address + " key = " + x.keys[i].key);
        }

        // check for key/children range order
        for(int i = 0; i < x.n; i++) {
            if(x.children[i] != -1L) {
                BTreeNode y = disk_read(x.children[i]);
                // check that y.keys[i] <= x.keys[i]
                if (i < y.n) {
                    if (!(y.keys[i].compareTo(x.keys[i]) <= 1)) {
                        System.err.println("x address = " + x.address + " y.keys[" + i + "] = " + y.keys[i].key + " > x.keys[" + i + "] = " + x.keys[i].key);
                    }
                }
            }
            if(x.children[i+1] != -1L) {
                BTreeNode z = disk_read(x.children[i+1]);
                // check that z.keys[i] > x.keys[i]
                if (i < z.n) {
                    if (!(z.keys[i].compareTo(x.keys[i]) > 1)) {
                        System.err.println("x address = " + x.address + " z.keys[" + i + "] = " + z.keys[i].key + " <= x.keys[" + i + "] = " + x.keys[i].key);
                    }
                }
            }
        }

        // check children
        for(int i = 0; i < x.children.length; i++) {
            BTreeNode w = null;
            if(x.children[i] != -1L) {
                w = disk_read(x.children[i]);
                check_valid_r(w);
            }
        }
    }

    public void check_valid() {
        System.err.println("Checking Btree validity");
        check_valid_r(root);
    }

    public int check_height_r(BTreeNode r) {
        if(r.isLeaf())
            return 1;
        else {
            ArrayList<Integer> ch = new ArrayList<>();
            for(int i = 0; i < r.nchildren(); i++) {
                BTreeNode y = disk_read(r.children[i]);
                ch.add(check_height_r(y));
            }
            Collections.sort(ch, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return a.compareTo(b);
                }
            });
            return ch.get(ch.size()-1) + 1;
        }
    }

    public int predicted_height() {
        if(num_nodes <= 1)
            return 1;
        long N = (num_nodes+1)/2;
        return (int)Math.ceil(Math.log10(N)/Math.log10(t));
    }

    public void check_height() {
        int p = predicted_height();
        System.err.println("Checking BTree height, predicted (n=" + num_nodes + ", t=" + t + ") <= " + p);
        int a = check_height_r(root);
        if(a > p)
            System.err.println("wrong height " + a);
        else
            System.err.println("height is good " + a);
    }
}
