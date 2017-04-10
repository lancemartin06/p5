import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class GeneBankSearch {

    public static void main(String[] args) {
        //
        // Process command line args
        //
        String use = "java GeneBankSearch <0/1(no/with Cache)> <btree file> <query file> [<cache size>] [<debug level>]";
        if(args.length < 3) {
            System.err.println("use " + use);
            System.exit(1);
        }
        int use_cache = Integer.parseInt(args[0]);
        String btree_file = args[1];
        String query_file = args[2];
        int cache_size = 0;
        int debug_level = 0;
        if(args.length > 3) {
            cache_size = Integer.parseInt(args[3]);
        }
        if(args.length > 4) {
            debug_level = Integer.parseInt(args[4]);
        }

        // find k, t from the filename
        int k = get_k(btree_file);
        if(k < 1 || k > 31) {
            System.err.println("k must be between 1 and 31");
            System.exit(1);
        }
        int t = get_t(btree_file);

        //
        // Point btree to disk btree_file
        //
        BTree btree = new BTree(k, t, btree_file);
        //if(debug_level > 0) {
        //    btree.print();
        //    btree.check_valid();
        //}

        //
        // Process query file
        // 
        FileReader input = null;
        try {
            input = new FileReader(query_file);
        } catch (FileNotFoundException ex) {
            System.err.println("query file " + query_file + " not found.  Exiting.");
            System.exit(1);
        }
        BufferedReader reader = new BufferedReader(input);
        String line;
        String seq = "";
        boolean start = false;
        try {
            while ((line = reader.readLine()) != null) {
                String search_seq = line.toLowerCase();
                Long search_key = GeneBankCreateBTree.seq_encode(search_seq);
                if(debug_level > 0) {
                    System.err.println("Searching for sequence " + search_seq + " = " + search_key); 
                }
                BTree.BTreeNode y = btree.search(search_key);
                if (y != null) {
                    int q = y.search_idx;
                    Long key = y.keys[q].key;
                    String decode = GeneBankCreateBTree.key_decode(key, k);
                    int f = y.keys[q].freq;
                    if(debug_level > 0) {
                        System.err.println("search_seq = " + search_seq + ", key =  " + key  + ", search_idx = " + q  + ", decode = " + decode + ", freq = " +  f);
                    }
                    else {
                        System.out.println(decode + ": " +  f);
                    }
                }
                //else {
                //    System.err.println(search_seq + ": 0");
                //}
            }
        } catch (IOException ex) {
            System.err.println("Error reading query file " + query_file + ".  Exiting.");
            System.exit(1);
        }
    }

    public static int get_k(String btree_file) {
        // example: GenBank_Sample_Record.gbk.btree.data.16.128
        String[] fields = btree_file.split("\\.");
        return Integer.parseInt(fields[4]);
    }    

    public static int get_t(String btree_file) {
        // example: GenBank_Sample_Record.gbk.btree.data.16.128
        String[] fields = btree_file.split("\\.");
        return Integer.parseInt(fields[5]);
    }
}
