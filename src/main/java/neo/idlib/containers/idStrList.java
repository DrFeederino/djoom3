package neo.idlib.containers;

import neo.idlib.Text.Str.idStr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/*
===============================================================================

idStrList re-implementation with ArrayLists

===============================================================================
*/
public class idStrList implements Comparator<idStr> {


    private List<idStr> stringsList;

    public idStrList() {
        this.stringsList = new ArrayList<>();
    }

    public idStrList(int size) {
        this.stringsList = new ArrayList<>(size);
    }

    /*
     ================
     idStrListSortPaths

     Sorts the list of path strings alphabetically and makes sure folders come first.
     (a, b) -> a.IcmpPath(b.toString()) is idStrList path sorting.
     ================
     */
    public static void idStrListSortPaths(neo.idlib.containers.idStrList strings) {
        if (strings.stringsList == null || strings.stringsList.isEmpty()) {
            return;
        }
        strings.getStringsList().sort((a, b) -> a.IcmpPath(b.toString()));
    }

    public List<idStr> getStringsList() {
        return stringsList;
    }

    /*
     ================
     idStrList::Sort

     Sorts the list of strings alphabetically. Creates a list of pointers to the actual strings and sorts the
     pointer list. Then copies the strings into another list using the ordered list of pointers.
     ================
     */
    public void sort() {
        if (stringsList.isEmpty()) {
            return;
        }

        stringsList.sort(this);
    }

    /*
     ================
     idStrList::SortSubSection

     Sorts a subsection of the list of strings alphabetically.
     ================
     */
    public void sortSubList(int startIndex, int endIndex) {
        if (stringsList.isEmpty() || startIndex >= endIndex) {
            return;
        }
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (endIndex > stringsList.size()) {
            endIndex = stringsList.size() - 1;
        }

        stringsList.subList(startIndex, endIndex).sort(this);
    }

    public int sizeStrings() {
        return stringsList.stream().mapToInt(idStr::Size).sum();
    }

    public int addUnique(String obj) {
        idStr newIdStr = new idStr(obj);
        int indexOfObj = stringsList.lastIndexOf(newIdStr);
        if (indexOfObj == -1) {
            return add(newIdStr); // assuming it's added to the end
        }
        return indexOfObj; // already in the array
    }

    public int addUnique(idStr obj) {
        int indexOfObj = stringsList.lastIndexOf(obj);
        if (indexOfObj == -1) {
            return add(obj); // assuming it's added to the end
        }
        return indexOfObj; // already in the array
    }

    public int add(String obj) {
        idStr newIdStr = new idStr(obj);
        stringsList.add(newIdStr);
        return stringsList.size() - 1;
    }

    public int add(idStr obj) {
        stringsList.add(obj);
        return stringsList.size() - 1;
    }

    public void resize(int newSize) {
        // free up the list if no data is being reserved
        if (newSize <= 0) {
            clear();
            return;
        }
        if (newSize == stringsList.size()) {
            // not changing the size, so just exit
            return;
        }
        int targetSize = Math.min(stringsList.size(), newSize);
        stringsList = stringsList.stream().limit(targetSize).collect(Collectors.toList());
    }

    @Deprecated
    // TODO: see if it's meaningful to clear in Java
    public void clear() {
        //this.stringsList = null;
    }

    public int insert(final idStr obj) {            // insert the element at the given index
        return insert(0, obj);
    }

    private int insert(int i, idStr obj) {
        stringsList.add(i, obj);
        return i;
    }


    /*
     ================
     idListSortCompare<idStrPtr>

     Compares two pointers to strings. Used to sort a list of string pointers alphabetically in idList<idStr>::Sort.
     ================
     */
    @Override
    public int compare(idStr a, idStr b) {
        return a.Icmp(b);
    }

    public Integer findIndex(idStr testVal) {
        int result = stringsList.indexOf(testVal);
        return result == -1 ? null : result;
    }

    public idStr get(int i) {
        if (i >= stringsList.size()) {
            i = stringsList.isEmpty() ? 0 : stringsList.size() - 1;
        }
        return stringsList.get(i);
    }

    public void set(idStrList associatedModels) {
        stringsList = new ArrayList<>(associatedModels.getStringsList());
    }

    public void SetGranularity(int i) {
        // ah yes granularity my favourite friend
        return;
    }

    public int size() {
        return stringsList.size();
    }

    public idStr addEmptyStr() {
        idStr idStr = new idStr();
        stringsList.add(idStr);
        return idStr;
    }

    public void setSize(int newNum, boolean resize) {
        if (resize || newNum > stringsList.size()) {
            resize(newNum);
        }
    }

    public void set(int i, String value) {
        if (i >= stringsList.size()) {
            i = stringsList.isEmpty() ? 0 : stringsList.size() - 1;
        }
        stringsList.add(i, new idStr(value));
    }

    public void set(int i, idStr obj) {
        if (i >= stringsList.size()) {
            i = stringsList.isEmpty() ? 0 : stringsList.size() - 1;
        }
        stringsList.add(i, obj);
    }

    public void setSize(int num) {
        setSize(num, true);
    }

    public void ensureSize(int num_files, idStr empty) {
        int sizeDiff = num_files - stringsList.size(); // e.g. num_files > list_size
        if (sizeDiff <= 0) {
            return; // it's ok
        }
        for (int i = 0; i < sizeDiff; i++) {
            stringsList.add(empty);
        }
    }

    public void removeAtIndex(int i) {
        if (i >= stringsList.size()) {
            i = stringsList.isEmpty() ? 0 : stringsList.size() - 1;
        }
        stringsList.remove(i);
    }

    public void remove(idStr idStr) {
        stringsList.remove(idStr);
    }

}
