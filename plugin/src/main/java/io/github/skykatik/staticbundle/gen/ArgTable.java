package io.github.skykatik.staticbundle.gen;

import java.util.Arrays;

class ArgTable {
    static final String[] EMPTY_STRING_ARRAY = new String[0];

    String[] names = EMPTY_STRING_ARRAY;
    int count;

    String add(int index, String name) {
        if (index >= names.length) {
            names = Arrays.copyOf(names, names.length + 4);
        }
        String currentName = names[index];
        if (currentName == null) {
            names[index] = name;
            count++;
            return null;
        }
        return currentName;
    }

    String name(int index) {
        return names[index];
    }

    int index(String name) {
        for (int i = 0; i < count; i++) {
            if (name.equals(names[i])) {
                return i;
            }
        }
        return -1;
    }

    int maxIndex() {
        return names.length;
    }

    boolean isEmpty() {
        return names == EMPTY_STRING_ARRAY;
    }

    int size() {
        return count;
    }

    void trim() {
        names = Arrays.copyOf(names, count);
    }
}
