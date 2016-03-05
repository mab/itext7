package com.itextpdf.kernel.pdf;

import com.itextpdf.io.LogMessageConstant;
import com.itextpdf.io.source.ByteUtils;
import com.itextpdf.kernel.PdfException;

import java.io.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfWriter extends PdfOutputStream implements Serializable{

    private static final byte[] obj = ByteUtils.getIsoBytes(" obj\n");
    private static final byte[] endobj = ByteUtils.getIsoBytes("\nendobj\n");
    private HashMap<ByteStore, PdfIndirectReference> streamMap = new HashMap<>();
    private final HashMap<Integer, Integer> serialized = new HashMap<>();

    private PdfOutputStream content = null;
    private byte[] buffer = null;

    /**
     * Indicates if the writer copy objects in a smart mode. If so PdfDictionary and PdfStream will be hashed
     * and reused if there's an object with the same content later.
     */
    private boolean smartMode;

    /**
     * Indicates if to use full compression (using object streams).
     */
    protected Boolean fullCompression;

    protected int compressionLevel = DEFAULT_COMPRESSION;

    /**
     * Currently active object stream.
     * Objects are written to the object stream if fullCompression set to true.
     */
    protected PdfObjectStream objectStream = null;

    protected Hashtable<Integer, PdfIndirectReference> copiedObjects = new Hashtable<>();

    //forewarned is forearmed
    protected boolean isUserWarnedAboutAcroFormCopying;

    public PdfWriter(java.io.OutputStream os) {
        super(new BufferedOutputStream(os));
        content = new PdfOutputStream(new ByteArrayOutputStream());
    }

    public PdfWriter(String filename) throws FileNotFoundException {
        this(new FileOutputStream(filename));
    }

    /**
     * Indicates if to use full compression mode.
     *
     * @return true if to use full compression, false otherwise.
     */
    public boolean isFullCompression() {
        return fullCompression != null ? fullCompression : false;
    }

    /**
     * Sets full compression mode.
     *
     * @param fullCompression true if to use full compression, false otherwise.
     */
    public PdfWriter setFullCompression(boolean fullCompression) {
        this.fullCompression = fullCompression;
        return this;
    }

    /**
     * Gets default compression level for @see PdfStream.
     * For more details @see {@link java.util.zip.Deflater}.
     *
     * @return compression level.
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    public ByteArrayOutputStream getByteArrayOutputStream() {
        return (ByteArrayOutputStream)content.getOutputStream();
    }

    /**
     * Sets default compression level for @see PdfStream.
     * For more details @see {@link java.util.zip.Deflater}.
     *
     * @param compressionLevel compression level.
     */
    public PdfWriter setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
        return this;
    }

    /**
     * Sets the smart mode.
     * <p/>
     * In smart mode when resources (such as fonts, images,...) are
     * encountered, a reference to these resources is saved
     * in a cache, so that they can be reused.
     * This requires more memory, but reduces the file size
     * of the resulting PDF document.
     */
    public PdfWriter setSmartMode(boolean smartMode) {
        this.smartMode = smartMode;
        return this;
    }

    @Override
    public void write(int b) throws java.io.IOException {
        super.write(b);
        content.write(b);
    }

    @Override
    public void write(byte[] b) throws java.io.IOException {
        super.write(b);
        content.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws java.io.IOException {
        super.write(b, off, len);
        content.write(b, off, len);
    }

    /**
     * Gets the current object stream.
     *
     * @return object stream.
     * @throws IOException
     * @throws PdfException
     */
    protected PdfObjectStream getObjectStream() throws IOException {
        if (!isFullCompression())
            return null;
        if (objectStream == null) {
            objectStream = new PdfObjectStream(document);
        } else if (objectStream.getSize() == PdfObjectStream.maxObjStreamSize) {
            objectStream.flush();
            objectStream = new PdfObjectStream(objectStream);
        }
        return objectStream;
    }

    /**
     * Flushes the object. Override this method if you want to define custom behaviour for object flushing.
     *
     * @param pdfObject     object to flush.
     * @param canBeInObjStm indicates whether object can be placed into object stream.
     * @throws IOException
     * @throws PdfException
     */
    protected void flushObject(PdfObject pdfObject, boolean canBeInObjStm) throws IOException {
        PdfIndirectReference indirectReference = pdfObject.getIndirectReference();
        if (isFullCompression() && canBeInObjStm) {
            PdfObjectStream objectStream = getObjectStream();
            objectStream.addObject(pdfObject);
        } else {
            indirectReference.setOffset(getCurrentPos());
            writeToBody(pdfObject);
        }
        indirectReference.setState(PdfObject.Flushed);
        indirectReference.clearState(PdfObject.MustBeFlushed);
        switch (pdfObject.getType()) {
            case PdfObject.Boolean:
            case PdfObject.Name:
            case PdfObject.Null:
            case PdfObject.Number:
            case PdfObject.String:
                ((PdfPrimitiveObject) pdfObject).content = null;
                break;
            case PdfObject.Array:
                PdfArray array = ((PdfArray) pdfObject);
                markArrayContentToFlush(array);
                array.releaseContent();
                break;
            case PdfObject.Stream:
            case PdfObject.Dictionary:
                PdfDictionary dictionary = ((PdfDictionary) pdfObject);
                markDictionaryContentToFlush(dictionary);
                dictionary.releaseContent();
                break;
            case PdfObject.IndirectReference:
                markObjectToFlush(((PdfIndirectReference) pdfObject).getRefersTo(false));
        }
    }

    private void markArrayContentToFlush(PdfArray array) {
        for (PdfObject item : array) {
            markObjectToFlush(item);
        }
    }

    private void markDictionaryContentToFlush(PdfDictionary dictionary) {
        for (PdfObject item : dictionary.values()) {
            markObjectToFlush(item);
        }
    }

    private void markObjectToFlush(PdfObject pdfObject) {
        if (pdfObject != null) {
            PdfIndirectReference indirectReference = pdfObject.getIndirectReference();
            if (indirectReference != null) {
                if (!indirectReference.checkState(PdfObject.Flushed)) {
                    indirectReference.setState(PdfObject.MustBeFlushed);
                }
            } else {
                if (pdfObject.getType() == PdfObject.IndirectReference) {
                    if (!pdfObject.checkState(PdfObject.Flushed)) {
                        pdfObject.setState(PdfObject.MustBeFlushed);
                    }
                } else if (pdfObject.getType() == PdfObject.Array) {
                    markArrayContentToFlush((PdfArray) pdfObject);
                } else if (pdfObject.getType() == PdfObject.Dictionary) {
                    markDictionaryContentToFlush((PdfDictionary) pdfObject);
                }
            }
        }
    }

    protected PdfObject copyObject(PdfObject object, PdfDocument document, boolean allowDuplicating) {
        if (object instanceof PdfIndirectReference)
            object = ((PdfIndirectReference) object).getRefersTo();
        if (object == null) {
            object = PdfNull.PdfNull;
        }
        if (checkTypeOfPdfDictionary(object, PdfName.Catalog)) {
            Logger logger = LoggerFactory.getLogger(PdfReader.class);
            logger.warn(LogMessageConstant.MAKE_COPY_OF_CATALOG_DICTIONARY_IS_FORBIDDEN);
            object = PdfNull.PdfNull;
        }

        PdfIndirectReference indirectReference = object.getIndirectReference();
        PdfIndirectReference copiedIndirectReference;

        int copyObjectKey = 0;
        if (!allowDuplicating && indirectReference != null) {
            copyObjectKey = getCopyObjectKey(object);
            copiedIndirectReference = copiedObjects.get(copyObjectKey);
            if (copiedIndirectReference != null)
                return copiedIndirectReference.getRefersTo();
        }

        if (smartMode && !checkTypeOfPdfDictionary(object, PdfName.Page)) {
            PdfObject copiedObject = smartCopyObject(object);
            if (copiedObject != null) {
                return copiedObjects.get(getCopyObjectKey(copiedObject)).getRefersTo();
            }
        }

        PdfObject newObject = object.newInstance();
        if (indirectReference != null) {
            if (copyObjectKey == 0)
                copyObjectKey = getCopyObjectKey(object);
            PdfIndirectReference in = newObject.makeIndirect(document).getIndirectReference();
            copiedObjects.put(copyObjectKey, in);
        }
        newObject.copyContent(object, document);

        return newObject;
    }

    /**
     * Writes object to body of PDF document.
     *
     * @param object object to write.
     * @throws IOException
     * @throws PdfException
     */
    protected void writeToBody(PdfObject object) throws IOException {
        if (crypto != null) {
            crypto.setHashKeyForNextObject(object.getIndirectReference().getObjNumber(), object.getIndirectReference().getGenNumber());
        }
        writeInteger(object.getIndirectReference().getObjNumber()).
                writeSpace().
                writeInteger(object.getIndirectReference().getGenNumber()).writeBytes(obj);
        write(object);
        writeBytes(endobj);
    }

    /**
     * Writes PDF header.
     *
     * @throws PdfException
     */
    protected void writeHeader() {
        writeByte('%').
                writeString(document.getPdfVersion().toString()).
                writeString("\n%\u00e2\u00e3\u00cf\u00d3\n");
    }

    /**
     * Flushes all objects which have not been flushed yet.
     *
     * @throws PdfException
     */
    protected void flushWaitingObjects() {
        PdfXrefTable xref = document.getXref();
        boolean needFlush = true;
        while (needFlush) {
            needFlush = false;
            for (int i = 1; i < xref.size(); i++) {
                PdfIndirectReference indirectReference = xref.get(i);
                if (indirectReference != null
                        && indirectReference.checkState(PdfObject.MustBeFlushed)) {
                    PdfObject object = indirectReference.getRefersTo(false);
                    if (object != null) {
                        object.flush();
                        needFlush = true;
                    }
                }
            }
        }
        if (objectStream != null && objectStream.getSize() > 0) {
            objectStream.flush();
            objectStream = null;
        }
    }

    /**
     * Flushes all modified objects which have not been flushed yet. Used in case incremental updates.
     *
     * @throws PdfException
     */
    protected void flushModifiedWaitingObjects() {
        PdfXrefTable xref = document.getXref();
        for (int i = 1; i < xref.size(); i++) {
            PdfIndirectReference indirectReference = xref.get(i);
            if (null != indirectReference) {
                PdfObject object = indirectReference.getRefersTo(false);
                if (object != null && !object.equals(objectStream) && object.isModified()) {
                    object.flush();
                }
            }
        }
        if (objectStream != null && objectStream.getSize() > 0) {
            objectStream.flush();
            objectStream = null;
        }
    }

    /**
     * Calculates hash code for object to be copied.
     * The hash code and the copied object is the stored in @{link copiedObjects} hash map to avoid duplications.
     *
     * @param object object to be copied.
     * @return calculated hash code.
     */
    protected int getCopyObjectKey(PdfObject object) {
        PdfIndirectReference in;
        if (object.isIndirectReference()) {
            in = (PdfIndirectReference) object;
        } else {
            in = object.getIndirectReference();
        }
        int result = in.hashCode();
        result = 31 * result + in.getDocument().hashCode();
        return result;
    }

    //method invoking while deserialization
    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.outputStream = new BufferedOutputStream(new ByteArrayOutputStream());
        content = new PdfOutputStream(new ByteArrayOutputStream());
        write(buffer);
        buffer = null;
    }

    private PdfObject smartCopyObject(PdfObject object) {
        ByteStore streamKey;
        if (object.isStream()) {
            streamKey = new ByteStore((PdfStream) object, serialized);
            PdfIndirectReference streamRef = streamMap.get(streamKey);
            if (streamRef != null) {
                return streamRef;
            }
            streamMap.put(streamKey, object.getIndirectReference());
        } else if (object.isDictionary()) {
            streamKey = new ByteStore((PdfDictionary) object, serialized);
            PdfIndirectReference streamRef = streamMap.get(streamKey);
            if (streamRef != null) {
                return streamRef.getRefersTo();
            }
            streamMap.put(streamKey, object.getIndirectReference());
        }

        return null;
    }

    //method invoking while serialization
    private void writeObject(java.io.ObjectOutputStream out)
            throws java.io.IOException {
        content.flush();
        buffer = getByteArrayOutputStream().toByteArray();
        out.defaultWriteObject();
    }

    private static boolean checkTypeOfPdfDictionary(PdfObject dictionary, PdfName expectedType) {
        return dictionary.isDictionary() && expectedType.equals(((PdfDictionary)dictionary).getAsName(PdfName.Type));
    }

    static class ByteStore {
        private final byte[] b;
        private final int hash;
        private MessageDigest md5;
        private void serObject(PdfObject obj, int level, ByteBuffer bb, HashMap<Integer, Integer> serialized) {
            if (level <= 0)
                return;
            if (obj == null) {
                bb.append("$Lnull");
                return;
            }
            PdfIndirectReference ref = null;
            ByteBuffer savedBb = null;

            if (obj.isIndirectReference()) {
                ref = (PdfIndirectReference)obj;
                Integer key = getCopyObjectKey(obj);
                if (serialized.containsKey(key)) {
                    bb.append(serialized.get(key));
                    return;
                }
                else {
                    savedBb = bb;
                    bb = new ByteBuffer();
                }
            }

            if (obj.isStream()) {
                bb.append("$B");
                serDic((PdfDictionary) obj, level - 1, bb, serialized);
                if (level > 0) {
                    md5.reset();
                    bb.append(md5.digest(((PdfStream)obj).getBytes(false)));
                }
            }
            else if (obj.isDictionary()) {
                serDic((PdfDictionary)obj, level - 1, bb, serialized);
            }
            else if (obj.isArray()) {
                serArray((PdfArray)obj, level - 1, bb, serialized);
            }
            else if (obj.isString()) {
                bb.append("$S").append(obj.toString());
            }
            else if (obj.isName()) {
                bb.append("$N").append(obj.toString());
            }
            else
                bb.append("$L").append(obj.toString());

            if (savedBb != null) {
                Integer key = getCopyObjectKey(ref);
                if (!serialized.containsKey(key))
                    serialized.put(key, calculateHash(bb.getBuffer()));
                savedBb.append(bb);
            }
        }

        private void serDic(PdfDictionary dic, int level, ByteBuffer bb, HashMap<Integer, Integer> serialized) {
            bb.append("$D");
            if (level <= 0)
                return;
            Object[] keys = dic.keySet().toArray();
            Arrays.sort(keys);
            for (int k = 0; k < keys.length; ++k) {
                if(keys[k].equals(PdfName.P) &&(dic.get((PdfName)keys[k]).isIndirectReference() || dic.get((PdfName)keys[k]).isDictionary()) || keys[k].equals(PdfName.Parent)) // ignore recursive call
                    continue;
                serObject((PdfObject) keys[k], level, bb, serialized);
                serObject(dic.get((PdfName) keys[k], false), level, bb, serialized);

            }
        }

        private void serArray(PdfArray array, int level, ByteBuffer bb, HashMap<Integer, Integer> serialized) {
            bb.append("$A");
            if (level <= 0)
                return;
            for (int k = 0; k < array.size(); ++k) {
                serObject(array.get(k, false), level, bb, serialized);
            }
        }

        ByteStore(PdfStream str, HashMap<Integer, Integer> serialized) {
            try {
                md5 = MessageDigest.getInstance("MD5");
            }
            catch (Exception e) {
                throw new PdfException(e);
            }
            ByteBuffer bb = new ByteBuffer();
            int level = 100;
            serObject(str, level, bb, serialized);
            this.b = bb.toByteArray();
            hash = calculateHash(this.b);
            md5 = null;
        }

        ByteStore(PdfDictionary dict, HashMap<Integer, Integer> serialized) {
            try {
                md5 = MessageDigest.getInstance("MD5");
            }
            catch (Exception e) {
                throw new PdfException(e);
            }
            ByteBuffer bb = new ByteBuffer();
            int level = 100;
            serObject(dict, level, bb, serialized);
            this.b = bb.toByteArray();
            hash = calculateHash(this.b);
            md5 = null;
        }

        private static int calculateHash(byte[] b) {
            int hash = 0;
            int len = b.length;
            for (int k = 0; k < len; ++k)
                hash = hash * 31 + (b[k] & 0xff);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ByteStore))
                return false;
            if (hashCode() != obj.hashCode())
                return false;
            return Arrays.equals(b, ((ByteStore)obj).b);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        protected int getCopyObjectKey(PdfObject object) {
            PdfIndirectReference in;
            if (object.isIndirectReference()) {
                in = (PdfIndirectReference) object;
            } else {
                in = object.getIndirectReference();
            }
            int result = in.hashCode();
            result = 31 * result + in.getDocument().hashCode();
            return result;
        }
    }
}
