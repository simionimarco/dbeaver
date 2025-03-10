/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jkiss.utils;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Common utils
 */
public class ArrayUtils {


    public static boolean isEmpty(@Nullable Object[] arr)
    {
        return arr == null || arr.length == 0;
    }

    public static boolean isEmpty(@Nullable short[] array)
    {
        return array == null || array.length == 0;
    }

    public static boolean isArray(@Nullable Object value) {
        return value != null && value.getClass().isArray();
    }

    public static boolean contains(@Nullable short[] array, short value)
    {
        if (array == null)
            return false;
        for (short item : array) {
            if (item == value)
                return true;
        }
        return false;
    }

    public static boolean contains(@Nullable char[] array, char value)
    {
        if (array == null || array.length == 0)
            return false;
        for (char c : array) {
            if (c == value)
                return true;
        }
        return false;
    }

    public static boolean isEmpty(@Nullable int[] array)
    {
        return array == null || array.length == 0;
    }

    public static boolean contains(@Nullable int[] array, int value)
    {
        if (array == null)
            return false;
        for (int v : array) {
            if (v == value)
                return true;
        }
        return false;
    }

    public static boolean isEmpty(@Nullable long[] array)
    {
        return array == null || array.length == 0;
    }

    public static boolean contains(@Nullable long[] array, long value)
    {
        if (array == null)
            return false;
        for (long v : array) {
            if (v == value)
                return true;
        }
        return false;
    }

    public static <OBJECT_TYPE> boolean contains(OBJECT_TYPE[] array, OBJECT_TYPE value)
    {
        if (isEmpty(array))
            return false;
        for (OBJECT_TYPE object_type : array) {
            if (CommonUtils.equalObjects(value, object_type))
                return true;
        }
        return false;
    }

    public static boolean containsIgnoreCase(String[] array, String value)
    {
        if (isEmpty(array) || value == null)
            return false;
        for (String s : array) {
            if (value.equalsIgnoreCase(s))
                return true;
        }
        return false;
    }

    public static <OBJECT_TYPE> boolean containsRef(@NotNull OBJECT_TYPE[] array, @Nullable OBJECT_TYPE value)
    {
        final int length = array.length;
        for (OBJECT_TYPE object_type : array) {
            if (value == object_type)
                return true;
        }
        return false;
    }

    @SafeVarargs
    public static <OBJECT_TYPE> boolean containsAny(OBJECT_TYPE[] array, OBJECT_TYPE... values) {
        for (OBJECT_TYPE item : array) {
            for (OBJECT_TYPE value : values) {
                if (CommonUtils.equalObjects(item, value))
                    return true;
            }
        }
        return false;
    }

    @SafeVarargs
    public static <OBJECT_TYPE> boolean containsAll(OBJECT_TYPE[] array, OBJECT_TYPE... values) {
        if (isEmpty(array)) {
            return false;
        }
        for (OBJECT_TYPE value : values) {
            if (!ArrayUtils.contains(array, value))
                return false;
        }
        return true;
    }

    @NotNull
    public static <T> T[] concatArrays(@NotNull T[] first, @NotNull T[] second)
    {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @NotNull
    public static <T> List<T> safeArray(@Nullable T[] array)
    {
        if (array == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(array);
        }
    }

    /**
     * Returns index of the first found element satisfying a given predicate in the provided array 
     */
    public static <T> int indexOf(@NotNull T[] array, @NotNull Predicate<T> condition) {
        for (int i = 0; i < array.length; i++) {
            if (condition.test(array[i])) {
                return i;
            }
        }
        return -1;
    }

    public static <T> int indexOf(T[] array, T element) {
        for (int i = 0; i < array.length; i++) {
            if (CommonUtils.equalObjects(array[i], element)) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(int[] array, int offset, int element) {
        for (int i = offset; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    public static int indexOf(byte[] array, int offset, byte element) {
        for (int i = offset; i < array.length; i++) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] deleteArea(Class<T> type, T[] elements, int from, int to) {
        int delCount = to - from + 1;
        T[] newArray = (T[]) Array.newInstance(type, elements.length - delCount);
        System.arraycopy(elements, 0, newArray, 0, from);
        if (to < elements.length - 1) {
            System.arraycopy(elements, to + 1, newArray, from, elements.length - from - delCount);
        }

        return newArray;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] insertArea(Class<T> type, Object[] elements, int pos, Object[] add) {
        T[] newArray = (T[]) Array.newInstance(type, elements.length + add.length);
        System.arraycopy(elements, 0, newArray, 0, pos);
        System.arraycopy(add, 0, newArray, pos, add.length);
        System.arraycopy(elements, pos, newArray, pos + add.length, elements.length - pos);
        return newArray;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] add(Class<T> type, T[] elements, T add) {
        T[] newArray = (T[]) Array.newInstance(type, elements.length + 1);
        System.arraycopy(elements, 0, newArray, 0, elements.length);
        newArray[elements.length] = add;
        return newArray;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] remove(Class<T> type, T[] elements, int index) {
        T[] newArray = (T[]) Array.newInstance(type, elements.length - 1);
        System.arraycopy(elements, 0, newArray, 0, index);
        if (index < elements.length - 1) {
            System.arraycopy(elements, index + 1, newArray, index, elements.length - index - 1);
        }
        return newArray;
    }

    public static <T> T[] remove(Class<T> type, T[] elements, T element) {
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] == element) {
                return remove(type, elements, i);
            }
        }
        return elements;
    }

    public static int[] add(int[] elements, int add) {
        int[] newArray = new int[elements.length + 1];
        System.arraycopy(elements, 0, newArray, 0, elements.length);
        newArray[elements.length] = add;
        return newArray;
    }

    public static void main(String[] args) {
        String[] arr = new String[0];

        for (int i = 0; i < 100; i++) {
            arr = add(String.class, arr, String.valueOf(i));
        }
        System.out.println(Arrays.toString(arr));
        for (int i = 0; i < 100; i++) {
            arr = remove(String.class, arr, 0);
        }
        System.out.println(Arrays.toString(arr));
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] toArray(Class<T> type, Collection<? extends T> list) {
        return list.toArray((T[]) Array.newInstance(type, list.size()));
    }

    @Nullable
    public static boolean[] unbox(@Nullable Boolean[] source) {
        if (source == null) {
            return null;
        }
        final boolean[] result = new boolean[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = source[i];
        }
        return result;
    }

    public static void reverse(@Nullable Object[] array) {
        if (array == null || array.length <= 1) {
            return;
        }
        for (int i = 0, j = array.length - 1; j > i; ++i, j--) {
            final Object tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
        }
    }
}
