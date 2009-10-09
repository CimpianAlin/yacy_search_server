// kelondroBase64Order.java
// -----------------------
// part of The Kelondro Database
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created 03.01.2006
//
// $LastChangedDate: 2009-01-30 15:48:11 +0100 (Fr, 30 Jan 2009) $
// $LastChangedRevision: 5539 $
// $LastChangedBy: orbiter $
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


package de.anomic.kelondro.order;

import java.io.UnsupportedEncodingException;
import java.util.Comparator;

import net.yacy.kelondro.logging.Log;


public class Base64Order extends AbstractOrder<byte[]> implements ByteOrder, Comparator<byte[]>, Cloneable {

    protected static final char[] alpha_standard = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    protected static final char[] alpha_enhanced = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    protected static final byte[] ahpla_standard = new byte[128];
    protected static final byte[] ahpla_enhanced = new byte[128];

    static {
        for (int i = 0; i < 128; i++) {
            ahpla_standard[i] = -1;
            ahpla_enhanced[i] = -1;
        }
        for (int i = 0; i < alpha_standard.length; i++) {
            ahpla_standard[alpha_standard[i]] = (byte) i;
            ahpla_enhanced[alpha_enhanced[i]] = (byte) i;
        }
    }

    private final Log log;

    public static final Base64Order standardCoder = new Base64Order(true, true);
    public static final Base64Order enhancedCoder = new Base64Order(true, false);

    private final boolean rfc1113compliant;
    private final char[] alpha;
    private final byte[] ahpla;
    private final byte[] ab; // decision table for comparisments

    public Base64Order(final boolean up, final boolean rfc1113compliant) {
        // if we choose not to be rfc1113compliant,
        // then we get shorter base64 results which are also filename-compatible
        this.rfc1113compliant = rfc1113compliant;
        this.asc = up;
        alpha = (rfc1113compliant) ? alpha_standard : alpha_enhanced;
        ahpla = (rfc1113compliant) ? ahpla_standard : ahpla_enhanced;
        ab = new byte[1 << 14];
        byte acc, bcc;
        byte c;
        // pre-compute comparisment results: this omits one single ahpla lookup during comparisment
        for (char ac: alpha) {
            for (char bc: alpha) {
                acc = ahpla[ac];
                bcc = ahpla[bc];
                c = 0;
                if (acc > bcc) c = 1;
                if (acc < bcc) c = -1;
                
                ab[(ac << 7) | bc] = c;
            } 
        }
        this.log = new Log("BASE64");
    }

    public static byte[] zero(int length) {
        final byte[] z = new byte[length];
        while (length > 0) { length--; z[length] = (byte) alpha_standard[0]; }
        return z;
    }
    
    public Order<byte[]> clone() {
        final Base64Order o = new Base64Order(this.asc, this.rfc1113compliant);
        o.rotate(zero);
        return o;
    }
    
    public final boolean wellformed(final byte[] a) {
        return wellformed(a, 0, a.length);
    }
    
    public final boolean wellformed(final byte[] a, final int astart, final int alength) {
        assert (astart + alength <= a.length) : "astart = " + astart + ", alength = " + alength + ", a.length = " + a.length;
        int b;
        for (int i = astart + alength - 1; i >= astart; i--) {
            b = a[i];
            if ((b < 0) || (b >= 128) || (ahpla[b] == -1)) return false;
        }
        return true;
    }
    
    public final static ByteOrder bySignature(final String signature) {
        if (signature.equals("Bd")) return new Base64Order(false, false);
        if (signature.equals("bd")) return new Base64Order(false, true);
        if (signature.equals("Bu")) return new Base64Order(true, false);
        if (signature.equals("bu")) return new Base64Order(true, true);
        return null;
    }
    
    public final String signature() {
        if ((!asc) && (!rfc1113compliant)) return "Bd";
        if ((!asc) && ( rfc1113compliant)) return "bd";
        if (( asc) && (!rfc1113compliant)) return "Bu";
        if (( asc) && ( rfc1113compliant)) return "bu";
        return null;
    }
    
    public final char encodeByte(final byte b) {
        return alpha[b];
    }

    public final byte decodeByte(final char b) {
        return ahpla[b];
    }

    public final String encodeLongSmart(final long c, int length) {
        if (c >= max(length)) {
            final StringBuilder s = new StringBuilder(length);
            s.setLength(length);
            while (length > 0) s.setCharAt(--length, alpha[63]);
            return new String(s);
        }
        return encodeLong(c, length);
    }

    public final String encodeLong(long c, int length) {
        final StringBuilder s = new StringBuilder(length);
        s.setLength(length);
        while (length > 0) {
            s.setCharAt(--length, alpha[(byte) (c & 0x3F)]);
            c >>= 6;
        }
        return new String(s);
    }

    public final byte[] encodeLongSubstr(long c, int length) {
        final byte[] s = new byte[length];
        while (length > 0) {
            s[--length] = (byte) alpha[(byte) (c & 0x3F)];
            c >>= 6;
        }
        return s;
    }

    public final void encodeLong(long c, final byte[] b, final int offset, int length) {
        assert offset + length <= b.length;
        while (length > 0) {
            b[--length + offset] = (byte) alpha[(byte) (c & 0x3F)];
            c >>= 6;
        }
    }
    
    public final long decodeLong(String s) {
        while (s.endsWith("=")) s = s.substring(0, s.length() - 1);
        long c = 0;
        for (int i = 0; i < s.length(); i++) c = (c << 6) | ahpla[s.charAt(i)];
        return c;
    }

    public final long decodeLong(final byte[] s, final int offset, int length) {
        while ((length > 0) && (s[offset + length - 1] == '=')) length--;
        long c = 0;
        for (int i = 0; i < length; i++) c = (c << 6) | ahpla[s[offset + i]];
        return c;
    }

    public static long max(final int len) {
        // computes the maximum number that can be coded with a base64-encoded
        // String of base len
        long c = 0;
        for (int i = 0; i < len; i++) c = (c << 6) | 63;
        return c;
    }

    public final String encodeString(final String in) {
        try {
            return encode(in.getBytes("UTF-8"));
        } catch (final UnsupportedEncodingException e) {
            return "";
        }
    }

    // we will use this encoding to encode strings with 2^8 values to
    // b64-Strings
    // we will do that by grouping each three input bytes to four output bytes.
    public final String encode(final byte[] in) {
        if (in == null || in.length == 0) return "";
        int lene = in.length / 3 * 4 + 3;
        StringBuilder out = new StringBuilder(lene);
        int pos = 0;
        long l;
        while (in.length - pos >= 3) {
            l = ((((0XffL & in[pos]) << 8) + (0XffL & in[pos + 1])) << 8) + (0XffL & in[pos + 2]);
            pos += 3;
            out = out.append(encodeLong(l, 4));
        }
        // now there may be remaining bytes
        if (in.length % 3 != 0) out = out.append((in.length % 3 == 2) ? encodeLong((((0XffL & in[pos]) << 8) + (0XffL & in[pos + 1])) << 8, 4).substring(0, 3) : encodeLong((((0XffL & in[pos])) << 8) << 8, 4).substring(0, 2));
        if (rfc1113compliant) while (out.length() % 4 > 0) out.append("=");
        // return result
        //assert lene == out.length() : "lene = " + lene + ", out.len = " + out.length();
        return new String(out);
    }
    
    public final byte[] encodeSubstring(final byte[] in, int sublen) {
        if (in.length == 0) return null;
        byte[] out = new byte[sublen];
        int writepos = 0;
        int pos = 0;
        long l;
        while (in.length - pos >= 3 && writepos < sublen) {
            l = ((((0XffL & in[pos]) << 8) + (0XffL & in[pos + 1])) << 8) + (0XffL & in[pos + 2]);
            pos += 3;
            System.arraycopy(encodeLongSubstr(l, 4), 0, out, writepos, 4);
            writepos += 4;
        }
        // now there may be remaining bytes
        if (in.length % 3 != 0 && writepos < sublen) {
            if (in.length % 3 == 2) {
                System.arraycopy(encodeLong((((0XffL & in[pos]) << 8) + (0XffL & in[pos + 1])) << 8, 4).getBytes(), 0, out, writepos, 3);
                writepos += 3;
            } else {
                System.arraycopy(encodeLong((((0XffL & in[pos])) << 8) << 8, 4).substring(0, 2).getBytes(), 0, out, writepos, 2);
                writepos += 2;
            }
        }
                                
        if (rfc1113compliant) while (writepos % 4 > 0 && writepos < sublen) out[writepos] = '=';
        assert encode(in).substring(0, sublen).equals(new String(out));
        return out;
    }

    public final String decodeString(final String in) {
        try {
            //return new String(decode(in), "ISO-8859-1");
            return new String(decode(in), "UTF-8");
        } catch (final java.io.UnsupportedEncodingException e) {
            System.out.println("internal error in base64: " + e.getMessage());
            return null;
        }
    }

    public final byte[] decode(String in) {
        if ((in == null) || (in.length() == 0)) return new byte[0];
        try {
            int posIn = 0;
            int posOut = 0;
            if (rfc1113compliant) while (in.charAt(in.length() - 1) == '=') in = in.substring(0, in.length() - 1);
            final byte[] out = new byte[in.length() / 4 * 3 + (((in.length() % 4) == 0) ? 0 : in.length() % 4 - 1)];
            long l;
            while (posIn + 3 < in.length()) {
                l = decodeLong(in.substring(posIn, posIn + 4));
                out[posOut + 2] = (byte) (l % 256);
                l = l / 256;
                out[posOut + 1] = (byte) (l % 256);
                l = l / 256;
                out[posOut] = (byte) (l % 256);
                l = l / 256;
                posIn += 4;
                posOut += 3;
            }
            if (posIn < in.length()) {
                if (in.length() - posIn == 3) {
                    l = decodeLong(in.substring(posIn) + "A");
                    l = l / 256;
                    out[posOut + 1] = (byte) (l % 256);
                    l = l / 256;
                    out[posOut] = (byte) (l % 256);
                    l = l / 256;
                } else {
                    l = decodeLong(in.substring(posIn) + "AA");
                    l = l / 256 / 256;
                    out[posOut] = (byte) (l % 256);
                    l = l / 256;
                }
            }
            return out;
        } catch (final ArrayIndexOutOfBoundsException e) {
            // maybe the input was not base64
            // throw new RuntimeException("input probably not base64");
            if (this.log.isFine()) this.log.logFine("wrong string receive: " + in);
            return new byte[0];
        }
    }

    private final long cardinalI(final String key) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        int p = 0;
        byte b;
        while ((p < 10) && (p < key.length())) {
            b = ahpla[key.charAt(p++)];
            if (b < 0) return -1;
            c = (c << 6) | b;
        }
        while (p++ < 10) c = (c << 6);
        c = (c << 3) | 7;
        assert c >= 0;
        return c;
    }

    private final long cardinalI(final byte[] key, int off, int len) {
        // returns a cardinal number in the range of 0 .. Long.MAX_VALUE
        long c = 0;
        int lim = off + Math.min(10, len);
        int lim10 = off + 10;
        byte b;
        while (off < lim) {
            b = key[off++];
            if (b < 0) return -1;
            b = ahpla[b];
            if (b < 0) return -1;
            c = (c << 6) | b;
        }
        while (off++ < lim10) c = (c << 6);
        c = (c << 3) | 7;
        assert c >= 0;
        return c;
    }
    
    public final byte[] uncardinal(long c) {
        c = c >> 3;
        byte[] b = new byte[12];
        for (int p = 9; p >= 0; p--) {
            b[p] = (byte) alpha[(int) (c & 0x3fL)];
            c = c >> 6;
        }
        b[10] = (byte) alpha[0x3f];
        b[11] = (byte) alpha[0x3f];
        return b;
    }
    
    public final long cardinal(final byte[] key) {
        if (this.zero == null) return cardinalI(key, 0, key.length);
        final long zeroCardinal = cardinalI(this.zero, 0, zero.length);
        final long keyCardinal = cardinalI(key, 0, key.length);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }
    
    public final long cardinal(final byte[] key, int off, int len) {
        if (this.zero == null) return cardinalI(key, off, len);
        final long zeroCardinal = cardinalI(this.zero, 0, zero.length);
        final long keyCardinal = cardinalI(key, off, len);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }
    
    public final long cardinal(final String key) {
        if (this.zero == null) return cardinalI(key);
        final long zeroCardinal = cardinalI(this.zero, 0, zero.length);
        final long keyCardinal = cardinalI(key);
        if (keyCardinal > zeroCardinal) return keyCardinal - zeroCardinal;
        return Long.MAX_VALUE - keyCardinal + zeroCardinal;
    }
    
    private static final int sig(final int x) {
        return (x > 0) ? 1 : (x < 0) ? -1 : 0;
    }
    
    public final boolean equal(final byte[] a, final byte[] b) {
        if ((a == null) && (b == null)) return true;
        if ((a == null) || (b == null)) return false;
        if (a.length != b.length) return false;
        int astart = 0;
        int bstart = 0;
        int length = a.length;
        while (length-- != 0) {
            if (a[astart++] != b[bstart++]) return false;
        }
        return true;
    }
    
    public final boolean equal(final byte[] a, int astart, final byte[] b, int bstart, int length) {
        if ((a == null) && (b == null)) return true;
        if ((a == null) || (b == null)) return false;
        while (length-- != 0) {
            if (a[astart++] != b[bstart++]) return false;
        }
        return true;
    }
   
    public final int compare(final byte[] a, final byte[] b) {
        try {
        return (asc) ?
                ((zero == null) ? compares(a, b) : compare0(a, 0, a.length, b, 0, b.length))
                :
                ((zero == null) ? compares(b, a) : compare0(b, 0, b.length, a, 0, a.length));
        } catch (Exception e) {
            // if a or b is not well-formed, an ArrayIndexOutOfBoundsException may occur
            // in that case we don't want that the exception makes databse functions
            // unusable and effective creates a showstopper. In such cases we apply
            // a different order on the objects and treat not well-formed objects
            // as bigger as all others. If both object are not well-formed, they are
            // compared with the natural order.
            boolean wfa = wellformed(a);
            boolean wfb = wellformed(b);
            if (wfa && wfb) {
                // uh strange. throw the exception
                if (e instanceof ArrayIndexOutOfBoundsException) throw (ArrayIndexOutOfBoundsException) e;
                throw new RuntimeException(e.getMessage());
            }
            if (wfa) return (asc) ? -1 :  1;
            if (wfb) return (asc) ?  1 : -1;
            return ((asc) ? 1 : -1) * NaturalOrder.naturalOrder.compare(a, b);
        }
    }

    public final int compare(final byte[] a, final int aoffset, final int alength, final byte[] b, final int boffset, final int blength) {
        try {
            return (asc) ?
                    compare0(a, aoffset, alength, b, boffset, blength)
                    :
                    compare0(b, boffset, blength, a, aoffset, alength);
        } catch (Exception e) {
            // same handling as in simple compare method above
            boolean wfa = wellformed(a, aoffset, alength);
            boolean wfb = wellformed(b, boffset, blength);
            if (wfa && wfb) {
                // uh strange. throw the exception
                if (e instanceof ArrayIndexOutOfBoundsException) throw (ArrayIndexOutOfBoundsException) e;
                throw new RuntimeException(e.getMessage());
            }
            if (wfa) return (asc) ? -1 :  1;
            if (wfb) return (asc) ?  1 : -1;
            return ((asc) ? 1 : -1) * NaturalOrder.naturalOrder.compare(a, aoffset, alength, b, boffset, blength);
        }
    }
    
    private final int compare0(final byte[] a, final int aoffset, final int alength, final byte[] b, final int boffset, final int blength) {
        if (zero == null) return compares(a, aoffset, alength, b, boffset, blength);

        // we have an artificial start point. check all combinations
        final int az = compares(a, aoffset, alength, zero, 0, Math.min(alength, zero.length)); // -1 if a < z; 0 if a == z; 1 if a > z
        final int bz = compares(b, boffset, blength, zero, 0, Math.min(blength, zero.length)); // -1 if b < z; 0 if b == z; 1 if b > z
        if (az == bz) return compares(a, aoffset, alength, b, boffset, blength);
        return sig(az - bz);
    }
    
    private final int compares(final byte[] a, final byte[] b) {
        assert (ahpla.length == 128);
        short i = 0;
        final int al = a.length;
        final int bl = b.length;
        final short ml = (short) Math.min(al, bl);
        byte ac, bc;
        while (i < ml) { // trace point
            assert (i < a.length) : "i = " + i + ", aoffset = " + 0 + ", a.length = " + a.length + ", a = " + NaturalOrder.arrayList(a, 0, al);
            assert (i < b.length) : "i = " + i + ", boffset = " + 0 + ", b.length = " + b.length + ", b = " + NaturalOrder.arrayList(b, 0, al);
            ac = a[i];
            assert (ac >= 0) && (ac < 128) : "ac = " + ac + ", a = " + NaturalOrder.arrayList(a, 0, al);
            bc = b[i];
            assert (bc >= 0) && (bc < 128) : "bc = " + bc + ", b = " + NaturalOrder.arrayList(b, 0, al);
            assert ac != 0;
            assert bc != 0;
            //if ((ac == 0) && (bc == 0)) return 0; // zero-terminated length
            if (ac != bc) return ab[(ac << 7) | bc];
            i++;
        }
        // compare length
        if (al > bl) return 1;
        if (al < bl) return -1;
        // they are equal
        return 0;
    }
    
    private final int compares(final byte[] a, final int aoffset, final int alength, final byte[] b, final int boffset, final int blength) {
        assert (aoffset + alength <= a.length) : "a.length = " + a.length + ", aoffset = " + aoffset + ", alength = " + alength;
        assert (boffset + blength <= b.length) : "b.length = " + b.length + ", boffset = " + boffset + ", blength = " + blength;
        assert (ahpla.length == 128);
        short i = 0;
        final int al = Math.min(alength, a.length - aoffset);
        final int bl = Math.min(blength, b.length - boffset);
        final short ml = (short) Math.min(al, bl);
        byte ac, bc;
        //byte acc, bcc;
        //int c = 0;
        while (i < ml) {
            assert (i + aoffset < a.length) : "i = " + i + ", aoffset = " + aoffset + ", a.length = " + a.length + ", a = " + NaturalOrder.arrayList(a, aoffset, al);
            assert (i + boffset < b.length) : "i = " + i + ", boffset = " + boffset + ", b.length = " + b.length + ", b = " + NaturalOrder.arrayList(b, boffset, al);
            ac = a[aoffset + i];
            assert (ac >= 0) && (ac < 128) : "ac = " + ac + ", a = " + NaturalOrder.arrayList(a, aoffset, al);
            bc = b[boffset + i];
            assert (bc >= 0) && (bc < 128) : "bc = " + bc + ", b = " + NaturalOrder.arrayList(b, boffset, al);
            assert ac != 0;
            assert bc != 0;
            //if ((ac == 0) && (bc == 0)) return 0; // zero-terminated length
            if (ac == bc) {
                // shortcut in case of equality: we don't need to lookup the ahpla value
                i++;
                continue;
            }
            //acc = ahpla[ac];
            //assert (acc >= 0) : "acc = " + acc + ", a = " + NaturalOrder.arrayList(a, aoffset, al) + "/" + new String(a, aoffset, al) + ", aoffset = 0x" + Integer.toHexString(aoffset) + ", i = " + i + "\n" + NaturalOrder.table(a, 16, aoffset);
            //bcc = ahpla[bc];
            //assert (bcc >= 0) : "bcc = " + bcc + ", b = " + NaturalOrder.arrayList(b, boffset, bl) + "/" + new String(b, boffset, bl) + ", boffset = 0x" + Integer.toHexString(boffset) + ", i = " + i + "\n" + NaturalOrder.table(b, 16, boffset);
            //if (acc > bcc) c = 1;
            //if (acc < bcc) c = -1;
            //assert c != 0;
            //assert ab[(ac << 7) | bc] == c;
            //return c;
            return ab[(ac << 7) | bc];
        }
        // compare length
        if (al > bl) return 1;
        if (al < bl) return -1;
        // they are equal
        return 0;
    }
    /*
    public final int comparePivot(final byte[] compiledPivot, final byte[] b, final int boffset, final int blength) {
        assert zero == null;
        assert asc;
        assert (boffset + blength <= b.length) : "b.length = " + b.length + ", boffset = " + boffset + ", blength = " + blength;
        int i = 0;
        final int bl = Math.min(blength, b.length - boffset);
        byte acc, bcc;
        assert boffset >= 0;
        assert boffset < b.length;
        assert boffset + Math.min(bl, compiledPivot.length) - 1 >= 0;
        assert boffset + Math.min(bl, compiledPivot.length) - 1 < b.length;
        byte bb;
        while ((i < compiledPivot.length) && (i < bl)) {
            acc = compiledPivot[i];
            assert boffset + i >= 0;
            assert boffset + i < b.length;
            bb = b[boffset + i];
            assert bb >= 0;
            assert bb < 128;
            bcc = ahpla[bb];
            assert (bcc >= 0) : "bcc = " + bcc + ", b = " + NaturalOrder.arrayList(b, boffset, bl) + "/" + new String(b, boffset, bl) + ", boffset = 0x" + Integer.toHexString(boffset) + ", i = " + i + "\n" + NaturalOrder.table(b, 16, boffset);
            if (acc > bcc) return 1;
            if (acc < bcc) return -1;
            // else the bytes are equal and it may go on yet undecided
            i++;
        }
        // compare length
        if (compiledPivot.length > bl) return 1;
        if (compiledPivot.length < bl) return -1;
        // they are equal
        return 0;
    }
    
    public final byte[] compilePivot(final byte[] a, final int aoffset, final int alength) {
        assert (aoffset + alength <= a.length) : "a.length = " + a.length + ", aoffset = " + aoffset + ", alength = " + alength;
        final byte[] cp = new byte[Math.min(alength, a.length - aoffset)];
        byte aa;
        for (int i = cp.length - 1; i >= 0; i--) {
            aa = a[aoffset + i];
            assert aa >= 0;
            assert aa < 128;
            cp[i] = ahpla[aa];
            assert cp[i] != -1;
        }
        return cp;
    }
*/
    public static void main(final String[] s) {
        // java -classpath classes de.anomic.kelondro.kelondroBase64Order
        final Base64Order b64 = new Base64Order(true, true);
        if (s.length == 0) {
            System.out.println("usage: -[ec|dc|es|ds|clcn] <arg>");
            System.exit(0);
        }
        if (s[0].equals("-ec")) {
            // generate a b64 encoding from a given cardinal
            System.out.println(b64.encodeLong(Long.parseLong(s[1]), 4));
        }
        if (s[0].equals("-dc")) {
            // generate a b64 decoding from a given cardinal
            System.out.println(b64.decodeLong(s[1]));
        }
        if (s[0].equals("-es")) {
            // generate a b64 encoding from a given string
            System.out.println(b64.encodeString(s[1]));
        }
        if (s[0].equals("-ds")) {
            // generate a b64 decoding from a given string
            System.out.println(b64.decodeString(s[1]));
        }
        if (s[0].equals("-cl")) {
            // return the cardinal of a given string as long value with the enhanced encoder
            System.out.println(Base64Order.enhancedCoder.cardinal(s[1].getBytes()));
        }
        if (s[0].equals("-cn")) {
            // return the cardinal of a given string as normalized float 0 .. 1 with the enhanced encoder
            System.out.println(((double) Base64Order.enhancedCoder.cardinal(s[1].getBytes())) / ((double) Long.MAX_VALUE));
        }
    }
}
