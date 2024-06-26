package freemarker3.cache;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import freemarker3.core.Environment;
import freemarker3.log.Logger;
import freemarker3.template.Configuration;
import freemarker3.template.Template;


/**
 * A class that performs caching and on-demand loading of the templates.
 * The actual loading is delegated to a {@link TemplateLoader}. Also,
 * various constructors provide you with convenient caches with predefined
 * behavior. Typically you don't use this class directly - in normal
 * circumstances it is hidden behind a {@link Configuration}.
 * @author Attila Szegedi, szegedia at freemail dot hu
 * @version $Id: TemplateCache.java,v 1.62 2004/03/17 01:14:33 ddekany Exp $
 */
public class TemplateCache 
{
    private static final String ASTERISKSTR = "*";
    private static final String LOCALE_SEPARATOR = "_";
    private static final char ASTERISK = '*';
    private static final String CURRENT_DIR_PATH_PREFIX = "./";
    private static final String CURRENT_DIR_PATH = "/./";
    private static final String PARENT_DIR_PATH_PREFIX = "../";
    private static final String PARENT_DIR_PATH = "/../";
    private static final char SLASH = '/';
    private static final Logger logger = Logger.getLogger("freemarker3.cache");

    private final TemplateLoader mainLoader;
    /** Here we keep our cached templates */
    private final CacheStorage storage;
    private final boolean isStorageConcurrent;
    /** The default refresh delay in milliseconds. */
    private long delay = 5000;
    /** Specifies if localized template lookup is enabled or not */
    private boolean localizedLookup = true;

    private Configuration config;

    /**
     * Returns a template cache that will first try to load a template from
     * the file system relative to the current user directory (i.e. the value
     * of the system property <code>user.dir</code>), then from the classpath.
     * This default template cache suits many applications.
     */
    public TemplateCache()
    {
        this(createDefaultTemplateLoader());
    }

    private static TemplateLoader createDefaultTemplateLoader() {
        try {
            return new FileTemplateLoader();
        } catch(Exception e) {
            logger.warn("Could not create a file template loader for current directory", e);
            return null;
        }
    }
    
    /**
     * Creates a new template cache with a custom template loader that is used
     * to load the templates.
     * @param loader the template loader to use.
     */
    public TemplateCache(TemplateLoader loader)
    {
        this(loader, new SoftCacheStorage());
    }

    /**
     * Creates a new template cache with a custom template loader and cache
     * storage that are used to load and store the templates.
     * @param loader the template loader to use.
     * @param storage the cache storage to use
     */
    public TemplateCache(TemplateLoader loader, CacheStorage storage)
    {
        this.mainLoader = loader;
        this.storage = storage;
        if(storage == null) 
        {
            throw new IllegalArgumentException("storage == null");
        }
        isStorageConcurrent = storage instanceof ConcurrentCacheStorage &&
            ((ConcurrentCacheStorage)storage).isConcurrent();
    }

    /**
     * Sets the configuration object to which this cache belongs. This method 
     * is called by the configuration itself to establish the relation, and 
     * should not be called by users.
     * @param config the configuration that this cache belongs to
     */
    public void setConfiguration(Configuration config)
    {
        this.config = config;
        clear();
    }

    /**
     * Returns the template loader used by this cache.
     * @return the template loader used by this cache.
     */
    public TemplateLoader getTemplateLoader() {
        return mainLoader;
    }
    
    /**
     * Returns the cache storage used by this cache.
     * @return the cache storage used by this cache.
     */
    public CacheStorage getCacheStorage() {
        return storage;
    }
    
    /**
     * Loads a template with the given name, in the specified locale and
     * using the specified character encoding.
     *
     * @param name the name of the template. Can't be null. The exact syntax of the name
     * is interpreted by the underlying {@link TemplateLoader}, but the
     * cache makes some assumptions. First, the name is expected to be
     * a hierarchical path, with path components separated by a slash
     * character (not with backslash!). The path (the name) must <em>not</em> begin with slash;
     * the path is always relative to the "template root directory".
     * Then, the <tt>..</tt> and <tt>.</tt> path metaelements will be resolved.
     * For example, if the name is <tt>a/../b/./c.ftl</tt>, then it will be
     * simplified to <tt>b/c.ftl</tt>. The rules regarding this are same as with conventional
     * UN*X paths. The path must not reach outside the template root directory, that is,
     * it can't be something like <tt>"../templates/my.ftl"</tt> (not even if the pervious path
     * happens to be equivalent with <tt>"/my.ftl"</tt>).
     * Further, the path is allowed to contain at most
     * one path element whose name is <tt>*</tt> (asterisk). This path metaelement triggers the
     * <i>acquisition mechanism</i>. If the template is not found in
     * the location described by the concatenation of the path left to the
     * asterisk (called base path) and the part to the right of the asterisk
     * (called resource path), the cache will attempt to remove the rightmost
     * path component from the base path ("go up one directory") and concatenate
     * that with the resource path. The process is repeated until either a
     * template is found, or the base path is completely exhausted.
     *
     * @param locale the requested locale of the template. Can't be null.
     * Assuming you have specified <code>en_US</code> as the locale and
     * <code>myTemplate.html</code> as the name of the template, the cache will
     * first try to retrieve <code>myTemplate_en_US.html</code>, then
     * <code>myTemplate_en.html</code>, and finally
     * <code>myTemplate.html</code>.
     *
     * @param encoding the character encoding used to interpret the template
     * source bytes. Can't be null.
     *
     * @param parse if true, the loaded template is parsed and interpreted
     * as a regular FreeMarker template. If false, the loaded template is
     * treated as an unparsed block of text.
     *
     * @return the loaded template, or null if the template is not found.
     * @throws IOException if an I/O exception occurs while loading the 
     * template
     */
    public Template getTemplate(String name, Locale locale, String encoding, boolean parse)
    throws IOException
    {
        if (name == null) {
            throw new IllegalArgumentException("Argument \"name\" can't be null");
        }
        if (locale == null) {
            throw new IllegalArgumentException("Argument \"locale\" can't be null");
        }
        if (encoding == null) {
            throw new IllegalArgumentException("Argument \"encoding\" can't be null");
        }
        name = normalizeName(name);
        if(name == null) {
            return null;
        }
        Template result = null;
        if (mainLoader != null) {
            result = getTemplate(mainLoader, name, locale, encoding, parse);
        }
        return result;
    }    
    
    private Template getTemplate(TemplateLoader loader, String name, Locale locale, String encoding, boolean parse)
    throws IOException
    {
        boolean debug = logger.isDebugEnabled();
        String debugName = debug ? name + "[" + locale + "," + encoding + (parse ? ",parsed] " : ",unparsed] ") : null;
        TemplateKey tk = new TemplateKey(name, locale, encoding, parse);
        CachedTemplate cachedTemplate;
        if(isStorageConcurrent) {
            cachedTemplate = (CachedTemplate)storage.get(tk);
        }
        else {
            synchronized(storage) {
                cachedTemplate = (CachedTemplate)storage.get(tk);
            }
        }
        long now = System.currentTimeMillis();
        long lastModified = -1L;
        Object newlyFoundSource = null;
        boolean rethrown = false;
        try {
            if (cachedTemplate != null) {
                // If we're within the refresh delay, return the cached copy
                if (now - cachedTemplate.lastChecked < delay) {
                    if(debug) {
                        logger.debug(debugName + "cached copy not yet stale; using cached.");
                    }
                    // Can be null, indicating a cached negative lookup
                    Object t = cachedTemplate.templateOrException;
                    if(t instanceof Template || t == null) {
                        return (Template)t;
                    }
                    else if(t instanceof RuntimeException) {
                        throwLoadFailedException((RuntimeException)t);
                    }
                    else if(t instanceof IOException) {
                        rethrown = true;
                        throwLoadFailedException((IOException)t);
                    }
                    throw new AssertionError("t is " + t.getClass().getName());
                }
                // Clone as the instance bound to the map should be treated as
                // immutable to ensure proper concurrent semantics
                cachedTemplate = cachedTemplate.cloneCachedTemplate();
                // Update the last-checked flag
                cachedTemplate.lastChecked = now;

                // Find the template source
                newlyFoundSource = findTemplateSource(name, locale);

                // Template source was removed
                if (newlyFoundSource == null) {
                    if(debug) {
                        logger.debug(debugName + "no source found.");
                    } 
                    storeNegativeLookup(tk, cachedTemplate, null);
                    return null;
                }

                // If the source didn't change and its last modified date
                // also didn't change, return the cached version.
                lastModified = loader.getLastModified(newlyFoundSource);
                boolean lastModifiedNotChanged = lastModified == cachedTemplate.lastModified;
                boolean sourceEquals = newlyFoundSource.equals(cachedTemplate.source);
                if(lastModifiedNotChanged && sourceEquals) {
                    if(debug) {
                        logger.debug(debugName + "using cached since " + 
                                newlyFoundSource + " didn't change.");
                    }
                    storeCached(tk, cachedTemplate);
                    return (Template)cachedTemplate.templateOrException;
                }
                else {
                    if(debug && !sourceEquals) {
                        logger.debug("Updating source, info for cause: " + 
                            "sourceEquals=" + sourceEquals + 
                            ", newlyFoundSource=" + newlyFoundSource + 
                            ", cachedTemplate.source=" + cachedTemplate.source);
                    }
                    if(debug && !lastModifiedNotChanged) {
                        logger.debug("Updating source, info for cause: " + 
                            "lastModifiedNotChanged=" + lastModifiedNotChanged + 
                            ", cache lastModified=" + cachedTemplate.lastModified + 
                            " != file lastModified=" + lastModified);
                    }
                    // Update the source
                    cachedTemplate.source = newlyFoundSource;
                }
            }
            else {
                if(debug) {
                    logger.debug("Could not find template in cache, "
                        + "creating new one; id=[" + 
                        tk.name + "[" + tk.locale + "," + tk.encoding + 
                        (tk.parse ? ",parsed] " : ",unparsed] ") + "]");
                }
                
                // Construct a new CachedTemplate entry. Note we set the
                // cachedTemplate.lastModified to Long.MIN_VALUE. This is
                // a flag that signs it has to be explicitly queried later on.
                cachedTemplate = new CachedTemplate();
                cachedTemplate.lastChecked = now;
                newlyFoundSource = findTemplateSource(name, locale);
                if (newlyFoundSource == null) {
                    storeNegativeLookup(tk, cachedTemplate, null);
                    return null;
                }
                cachedTemplate.source = newlyFoundSource;
                cachedTemplate.lastModified = lastModified = Long.MIN_VALUE;
            }
            if(debug) {
                logger.debug("Compiling FreeMarker template " + 
                    debugName + " from " + newlyFoundSource);
            }
            // If we get here, then we need to (re)load the template
            Object source = cachedTemplate.source;
            Template t = 
                loadTemplate(loader, name, locale, encoding, parse, source);
            cachedTemplate.templateOrException = t;
            cachedTemplate.lastModified =
                lastModified == Long.MIN_VALUE
                    ? loader.getLastModified(source)
                    : lastModified;
            storeCached(tk, cachedTemplate);
            return t;
        }
        catch(RuntimeException e) {
            storeNegativeLookup(tk, cachedTemplate, e);
            throw e;
        }
        catch(IOException e) {
            if(!rethrown) {
                storeNegativeLookup(tk, cachedTemplate, e);
            }
            throw e;
        }
        finally {
            if(newlyFoundSource != null) {
                loader.closeTemplateSource(newlyFoundSource);
            }
        }
    }

    private void throwLoadFailedException(Exception e) throws IOException {
        IOException ioe = new IOException("There was an error loading the " +
                "template on an earlier attempt; it is attached as a cause");
        ioe.initCause(e);
        throw ioe;
    }

    private void storeNegativeLookup(TemplateKey tk, 
            CachedTemplate cachedTemplate, Exception e) {
        cachedTemplate.templateOrException = e;
        cachedTemplate.source = null;
        cachedTemplate.lastModified = 0L;
        storeCached(tk, cachedTemplate);
    }

    private void storeCached(TemplateKey tk, CachedTemplate cachedTemplate) {
        if(isStorageConcurrent) {
            storage.put(tk, cachedTemplate);
        }
        else {
            synchronized(storage) {
                storage.put(tk, cachedTemplate);
            }
        }
    }

    private Template loadTemplate(TemplateLoader loader, String name, 
            Locale locale, String encoding, boolean parse, Object source)
    throws IOException
    {
        Template template;
        Reader reader = loader.getReader(source, encoding);
        try {
            if(parse) {
                template = new Template(name, reader, config, encoding);
                template.setLocale(locale);
            }
            else {
                // Read the contents into a StringWriter, then construct a single-textblock
                // template from it.
                StringWriter sw = new StringWriter();
                char[] buf = new char[4096];
                for(;;) {
                    int charsRead = reader.read(buf);
                    if (charsRead > 0) {
                        sw.write(buf, 0, charsRead);
                    }
                    else if(charsRead == -1) {
                        break;
                    }
                }
                template = Template.getPlainTextTemplate(name, sw.toString(), config);
                template.setLocale(locale);
            }
            template.setEncoding(encoding);
        }
        finally {
            reader.close();
        }
        return template;
    }

    /**
     * Gets the delay in milliseconds between checking for newer versions of a
     * template source.
     * @return the current value of the delay
     */
    public synchronized long getDelay()
    {
        return delay;
    }

    /**
     * Sets the delay in milliseconds between checking for newer versions of a
     * template sources.
     * @param delay the new value of the delay
     */
    public synchronized void setDelay(long delay)
    {
        this.delay = delay;
    }

    /**
     * Returns if localized template lookup is enabled or not.
     * @return true if localized template lookup is enabled, false otherwise.
     */
    public synchronized boolean getLocalizedLookup()
    {
        return localizedLookup;
    }

    /**
     * Sets whether to enable localized template lookup or not.
     * @param localizedLookup true to enable localized template lookup, false 
     * to disable it.
     */
    public synchronized void setLocalizedLookup(boolean localizedLookup)
    {
        this.localizedLookup = localizedLookup;
    }

    /**
     * Removes all entries from the cache, forcing reloading of templates
     * on subsequent {@link #getTemplate(String, Locale, String, boolean)}
     * calls.
     */
    public void clear()
    {
        synchronized (storage) {
            storage.clear();
            if(mainLoader instanceof StatefulTemplateLoader) {
                ((StatefulTemplateLoader)mainLoader).resetState();
            }
        }
    }

    /**
     * Resolves a relative template path to absolute template path for purposes
     * of an inclusion via the include directive. Can only be invoked during 
     * template processing.
     * @param env the Environment associated with the current thread.
     * @param parentTemplateDir the absolute directory path of the template
     * containing the include directive.
     * @param templateNameString the potentially relative path specified in the
     * include directive.
     * @return the absolute path resolved against the specified parent 
     * directory.
     */
    public static String getFullTemplatePath(Environment env, String parentTemplateDir, String templateNameString)
    {
        if (templateNameString.indexOf("://") >0) {
           ;
        }
        else if (templateNameString.length() > 0 && templateNameString.charAt(0) == '/')  {
            int protIndex = parentTemplateDir.indexOf("://");
            if (protIndex >0) {
                templateNameString = parentTemplateDir.substring(0, protIndex + 2) + templateNameString;
            } else {
                templateNameString = templateNameString.substring(1);
            }
        }
        else {
            templateNameString = parentTemplateDir + templateNameString;
        }
        return templateNameString;
    }

    private Object findTemplateSource(String name, Locale locale)
    throws
    	IOException
    {
        if (getLocalizedLookup()) {
            int lastDot = name.lastIndexOf('.');
            String prefix = lastDot == -1 ? name : name.substring(0, lastDot);
            String suffix = lastDot == -1 ? "" : name.substring(lastDot);
            String localeName = LOCALE_SEPARATOR + locale.toString();
            StringBuilder buf = new StringBuilder(name.length() + localeName.length());
            buf.append(prefix);
            for (;;) {
                buf.setLength(prefix.length());
                String path = buf.append(localeName).append(suffix).toString();
                Object templateSource = acquireTemplateSource(path);
                if (templateSource != null) {
                    return templateSource;
                }
                int lastUnderscore = localeName.lastIndexOf('_');
                if (lastUnderscore == -1) {
                    break;
                }
                localeName = localeName.substring(0, lastUnderscore);
            }
            return null;
        }
        else {
            return acquireTemplateSource(name);
        }
    }

    private Object acquireTemplateSource(String path) throws IOException
    {
        int asterisk = path.indexOf(ASTERISK);
        // Shortcut in case there is no acquisition
        if(asterisk == -1) {
            return mainLoader.findTemplateSource(path);
        }
        StringTokenizer tok = new StringTokenizer(path, "/");
        int lastAsterisk = -1;
        List<String> tokpath = new ArrayList<String>();
        while(tok.hasMoreTokens()) {
            String pathToken = tok.nextToken();
            if(pathToken.equals(ASTERISKSTR)) {
                if(lastAsterisk != -1) {
                    tokpath.remove(lastAsterisk);
                }
                lastAsterisk = tokpath.size();
            }
            tokpath.add(pathToken);
        }
        String basePath = concatPath(tokpath, 0, lastAsterisk);
        String resourcePath = concatPath(tokpath, lastAsterisk + 1, 
                tokpath.size());
        if(resourcePath.endsWith("/")) {
            resourcePath = resourcePath.substring(0, resourcePath.length() - 1);
        }

        StringBuilder buf = new StringBuilder(path.length()).append(basePath);
        int l = basePath.length();
        boolean debug = logger.isDebugEnabled();
        for(;;) {
            String fullPath = buf.append(resourcePath).toString();
            if(debug) {
                logger.debug("Trying to find template source " + fullPath);
            }
            Object templateSource = mainLoader.findTemplateSource(fullPath);
            if(templateSource != null) {
                return templateSource;
            }
            if(l == 0) {
                return null;
            }
            l = basePath.lastIndexOf(SLASH, l - 2) + 1;
            buf.setLength(l);
        }
    }

    private static String concatPath(List path, int from, int to)
    {
        StringBuilder buf = new StringBuilder((to - from) * 16);
        for(int i = from; i < to; ++i) {
            buf.append(path.get(i)).append('/');
        }
        return buf.toString();
    }
    
    private static String normalizeName(String name) {
        if (name.indexOf("://") >0) {
            return name;
        }
        for(;;) {
            int parentDirPathLoc = name.indexOf(PARENT_DIR_PATH);
            if(parentDirPathLoc == 0) {
                // If it starts with /../, then it reaches outside the template
                // root.
                return null;
            }
            if(parentDirPathLoc == -1) {
                if(name.startsWith(PARENT_DIR_PATH_PREFIX)) {
                    // Another attempt to reach out of template root.
                    return null;
                }
                break;
            }
            int previousSlashLoc = name.lastIndexOf(SLASH, parentDirPathLoc - 1);
            name = name.substring(0, previousSlashLoc + 1) +
                   name.substring(parentDirPathLoc + PARENT_DIR_PATH.length());
        }
        for(;;) {
            int currentDirPathLoc = name.indexOf(CURRENT_DIR_PATH);
            if(currentDirPathLoc == -1) {
                if(name.startsWith(CURRENT_DIR_PATH_PREFIX)) {
                    name = name.substring(CURRENT_DIR_PATH_PREFIX.length());
                }
                break;
            }
            name = name.substring(0, currentDirPathLoc) +
                   name.substring(currentDirPathLoc + CURRENT_DIR_PATH.length() - 1);
        }
        // Editing can leave us with a leading slash; strip it.
        if(name.length() > 1 && name.charAt(0) == SLASH) {
            name = name.substring(1);
        }
        return name;
    }

    /**
     * This class holds a (name, locale) pair and is used as the key in
     * the cached templates map.
     */
    private static final class TemplateKey
    {
        private final String name;
        private final Locale locale;
        private final String encoding;
        private final boolean parse;

        TemplateKey(String name, Locale locale, String encoding, boolean parse)
        {
            this.name = name;
            this.locale = locale;
            this.encoding = encoding;
            this.parse = parse;
        }

        public boolean equals(Object o)
        {
            if (o instanceof TemplateKey) {
                TemplateKey tk = (TemplateKey)o;
                return
                    parse == tk.parse &&
                    name.equals(tk.name) &&
                    locale.equals(tk.locale) &&
                    encoding.equals(tk.encoding);
            }
            return false;
        }

        public int hashCode()
        {
            return
                name.hashCode() ^
                locale.hashCode() ^
                encoding.hashCode() ^
                (parse ? Boolean.FALSE : Boolean.TRUE).hashCode();
        }
    }

    /**
     * This class holds the cached template and associated information
     * (the source object, and the last-checked and last-modified timestamps).
     * It is used as the value in the cached templates map. Note: this class
     * is Serializable to allow custom 3rd party CacheStorage implementations 
     * to serialize/replicate them; FreeMarker code itself doesn't rely on its
     * serializability.
     */
    private static final class CachedTemplate implements Cloneable
    {
        Object templateOrException;
        Object source;
        long lastChecked;
        long lastModified;
        
        public CachedTemplate cloneCachedTemplate() {
            try {
                return (CachedTemplate)super.clone();
            }
            catch(CloneNotSupportedException e) {
                throw new UndeclaredThrowableException(e);
            }
        }
    }
}
