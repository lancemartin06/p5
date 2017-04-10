import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class GeneBankCreateBTree {

    public static void main(String[] args) {

        //
        // Process command line args
        //
        String use = "java GeneBankCreateBTree <0/1(no/with Cache)> <degree> <gbk file> <sequence length> [<cache size>] [<debug level>]";
        if(args.length < 4) {
            System.err.println("use " + use);
            System.exit(1);
        }
        int use_cache = Integer.parseInt(args[0]);
        int degree = Integer.parseInt(args[1]);
        String gbk_file = args[2];
        int seq_len = Integer.parseInt(args[3]);
        int cache_size = 0;
        int debug_level = 0;
        if(args.length > 4) {
            cache_size = Integer.parseInt(args[4]);
        }
        if(args.length > 5) {
            debug_level = Integer.parseInt(args[5]);
        }

        //
        // Parse gbk file
        //
        ArrayList<String> gbk_seqs = parse_gbk_file(gbk_file);
        if(debug_level > 0) {
            System.err.println("there are " + gbk_seqs.size() + " sequences in file " + gbk_file);
            for(String s : gbk_seqs) {
                System.err.println(s);
                System.err.println();
            }
            System.err.flush();
        }

        HashMap<String, Integer> gbk_subs = new HashMap<>();
        boolean UseHashMap = true;

        if(UseHashMap) {
            // Store substrings/counts in HashMap
            for(int i = 0; i < gbk_seqs.size(); i++) {
                HashMap<String, Integer> subs = substrings(gbk_seqs.get(i), seq_len);
                gbk_subs = hash_add(gbk_subs, subs);
            }
    
            // print HashMap
            if(false) {
                for (Map.Entry<String, Integer> entry : gbk_subs.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                }
            }
        }

        //
        //
        // Insert subsequences into BTree
        //
        if(degree == 0) { // optimize for 4096 byte block read/write
            // NODE_BYTES = 8 + 12*(2*t-1) + 8*2t;
            // NODE_BYTES = 8 + 24*t-12 + 16t;
            // NODE_BYTES = 40t-4;
            // 4096 = 40t - 4;
            // 4100/40 = t
            degree = 102;
        }

        String output_file = gbk_file.concat(".btree.data." + seq_len + "." + degree);

        // Since we are creating the btree, if the file exists, remove it
        File f = new File(output_file);
        if(f.exists() && !f.isDirectory()) {
            f.delete();
        }

        //                         k       t     filename
        BTree btree = new BTree(seq_len, degree, output_file);
        Long key = null;

        if(UseHashMap) {
            int print_count = 0;
            int max_subs = gbk_subs.size();
            if(debug_level > 0) {
                System.err.println("gbk_subs size() is " + gbk_subs.size());
            }
            for (Map.Entry<String, Integer> entry : gbk_subs.entrySet()) {
                String seq = entry.getKey();
                int freq = entry.getValue();
                key = seq_encode(seq);
    
                // check encoding
                String seqa = key_decode(key, seq_len);
                if(!seqa.equals(seq)) {
                    System.err.println("bad encoding " + seq + " -> " + key + " -> " + seqa);
                    System.exit(1);
                }
                if(debug_level > 0) {
                    System.out.println("inserting " + seq + ": " + freq);
                }
                for(int i = 0; i < freq; i++) { // TODO !!! fix this so that insert takes freq value???
                    btree.insert(key);
                }
    
                if(debug_level == 0) {
                    print_count = print_a_dot(max_subs, print_count); 
                }
            }
            System.err.println();
        }
        else {
            // find substring one at a time and insert into tree
            for(int i = 0; i < gbk_seqs.size(); i++) {
                insert_subs_one_at_a_time(gbk_seqs.get(i), seq_len, btree, debug_level);
            }
        }

        if(debug_level > 0) {
            btree.print();
        }

        if(debug_level > 0) {
            // check if btree is a valid btree
            btree.check_valid();
            btree.check_height();
        }
    }

    public static String key_decode(Long key, int k) {
        String result = "";
        for(long i = 0; i < (long)k; i++) {
            long digit = ((key & (3L<<2L*i)) >> 2L*i);
            if(digit == 0L) {
                result = "a".concat(result);
            }
            else if(digit == 3L) {
                result = "t".concat(result);
            }
            else if(digit == 1L) {
                result = "c".concat(result);
            }
            else if (digit == 2L) {
                result = "g".concat(result);
            }
        }
        return result;
    }

    public static Long seq_encode(String s) {
        Long key = 0L;
        int j = 0;
        long digit = 0;
        for(int i = s.length()-1; i >= 0; i--) {
            switch(s.charAt(i)) {
                case 'a':
                    digit = 0;
                    break;
                case 't':
                    digit = 3;
                    break;
                case 'c':
                    digit = 1;
                    break;
                case 'g':
                    digit = 2;
                    break;
            }
            key = ((digit&3) << j) | key;
            j += 2;
        }
        return key;
    }

    public static ArrayList<String> parse_gbk_file(String gbk_file) {
        ArrayList<String> seqs = new ArrayList<>();
        FileReader input = null;
        try {
            input = new FileReader(gbk_file);
        } catch (FileNotFoundException ex) {
            System.err.println("can't open gbk file. Exiting");
            System.exit(1);
        }
        BufferedReader reader = new BufferedReader(input);
        String line;
        String seq = "";
        boolean start = false;
        try {
            while ((line = reader.readLine()) != null) {
                if(line.contains("ORIGIN")) {
                    start = true;
                    seq = "";
                }
                else if(line.contains("//") && start) {
                    start = false;
                    seqs.add(seq);
                }
                else if(start) {
                    // replace white space and numbers and concat
                    seq = seq.concat(line.replaceAll("[0-9\\s]", "")).toLowerCase();
                }
            }
        } catch (IOException ex) {
            System.err.println("can't read gbk file. Exiting");
            System.exit(1);
        }

        return seqs;
    }

    static HashMap<String, Integer> substrings(String str, int len) {
        HashMap<String, Integer> subs = new HashMap<>();
        String substr = "";
        for (int i = 0; i + len < str.length() + 1; i++) {
            substr = str.substring(i, i + len);
            if (!substr.equals("") && substr.indexOf('n') == -1 && substr.indexOf('N') == -1) {
                Integer count = subs.get(substr);
                if(count == null) {
                    subs.put(substr, 1);
                }
                else {
                    subs.put(substr, count + 1);
                }
            }
        }
        if(false) {
            System.err.println("substring hashmap:");
            for(Map.Entry<String, Integer> entry : subs.entrySet()) {
                System.err.println(entry.getKey() + ": " + entry.getValue());
            }
        }
        return subs;
    }

    static int print_a_dot(int max_count, int current_count) {
        // print a dot to the screen to keep the user from pressing CTRL-C 
        if(max_count > 80 && current_count%10 == 0) { 
            int modulus = 10*(max_count/80);
            System.err.printf(".");
        }
        else if(max_count <= 80) {
            System.err.printf(".");
        }
        return current_count + 1;
    }

    static void insert_subs_one_at_a_time(String str, int len, BTree btree, int debug_level) {
        String substr = "";
        int max_subs = str.length() - len + 1;  // this is max number of subs without n characters
        int print_count = 0;
        for (int i = 0; i + len < str.length() + 1; i++) {
            substr = str.substring(i, i + len);
            if (!substr.equals("") && substr.indexOf('n') == -1 && substr.indexOf('N') == -1) {
                if(!key_decode(seq_encode(substr), len).equals(substr)) {
                    System.err.println("bad encode");
                    System.err.println("substr = " + substr + ", encode = " + seq_encode(substr) + ", decode = " + key_decode(seq_encode(substr), len));
                    System.exit(1);
                }
                btree.insert(seq_encode(substr));
                if(debug_level == 0) {
                    print_count = print_a_dot(max_subs, print_count); 
                }
            }
        }
    }

    static HashMap<String, Integer> hash_add(HashMap<String, Integer> dest, HashMap<String, Integer> src) {
        // add counts from src into dest
        if(dest.size() == 0) { // if dest is empty
            return src;
        }

        for (Map.Entry<String, Integer> entry : src.entrySet()) {
            String key = entry.getKey();
            int src_value = entry.getValue();
            Integer dest_value = dest.get(key);
            if(dest_value == null) {
                dest.put(key, src_value);
            }
            else {
                dest.put(key, src_value + dest_value);
            }
        }
        return dest;
    }
}
