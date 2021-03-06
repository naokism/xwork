/*
 * Copyright (c) 2002-2007 by OpenSymphony
 * All rights reserved.
 */
package com.opensymphony.xwork.util;

import com.opensymphony.xwork.XWorkConstants;
import com.opensymphony.xwork.XworkException;
import com.opensymphony.xwork.config.ConfigurationManager;
import ognl.*;
import ognl.enhance.ExpressionAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Utility class that provides common access to the <a href="www.ognl.org">Ognl</a> APIs for
 * setting and getting properties from objects (usually Actions).
 *
 * <!-- START SNIPPET: javadoc1 -->
 *
 * Ognl by default uses expression parsing for expression evaluation. However starting from Ognl 2.7.1, Ognl is capable
 * of doing expression compilation using <a href="http://www.csg.is.titech.ac.jp/~chiba/javassist">Javassist</a> for
 * expression evaluation. WebWork 2.2.7 / XWork1.2.4 and above support this functionality, however it is turn off by default to
 * preserved backwards compatibilities. To turn it on, we need to use the following Doctype :-
 *
 * <!-- END SNIPPET: javadoc1 -->
 *
 * <pre>
 * <!-- START SNIPPET: dtd -->
 *     &lt;!DOCTYPE xwork PUBLIC
 *	                    "-//OpenSymphony Group//XWork 1.1.2//EN"
 *	                    "http://www.opensymphony.com/xwork/xwork-1.1.2.dtd"&gt;
 * <!-- END SNIPPET: dtd -->
 * </pre>
 *
 * <!-- START SNIPPET: javadoc2 -->
 *
 * And also declare a parameter "useOgnlEnhancement" with value "true" in xwork.xml as follows.
 *
 * <!-- END SNIPPET: javadoc2 -->
 *
 * <pre>
 * <!-- START SNIPPET: sample -->
 * &lt;xwork&gt;
 *    &lt;parameters&gt;
 *      &lt;!-- enable OGNL expression compilation feature --&gt;
 *      &lt;parameter name="useOgnlEnhancement" value="true" /&gt;
 *      ...
 *    &lt;/parameters&gt;
 *    ...
 *  &lt;/xwork&gt;
 * <!-- END SNIPPET: sample -->
 * </pre>
 *
 * <!-- START SNIPPET: javadoc3 -->
 *
 * NOTE: Do take note that parameters declared in xwork.xml or its included files will override where the latter takes
 * precedence. For example if we have a parameter declared in xwork.xml and one in xwork-include1.xml which is included
 * through xwork.xml, the parameter declared in xwork-include1.xml will override the one declared in xwork.xml. In
 * other words the parameter declared in xwork-include1.xml takes precedence.
 *
 * <!-- END SNIPPET: javadoc3 -->
 *
 *
 * @author Jason Carreira
 * @author tmjee
 * @version $Date: 2008-06-14 10:52:51 +0900 (土, 14 6 2008) $ $Id: OgnlUtil.java 1823 2008-06-14 01:52:51Z tm_jee $
 */
public class OgnlUtil {

    private static final Log log = LogFactory.getLog(OgnlUtil.class);
    private static HashMap parsedExpressions = new HashMap();
    private static HashMap beanInfoCache = new HashMap();

    private static Boolean useOgnlEnhancement = null;


    /**
     * Sets the object's properties using the default type converter, defaulting to not throw
     * exceptions for problems setting the properties.
     *
     * @param props   the properties being set
     * @param o       the object
     * @param context the action context
     */
    public static void setProperties(Map props, Object o, Map context) {
        setProperties(props, o, context, false);
    }

    /**
     * Sets the object's properties using the default type converter.
     *
     * @param props                   the properties being set
     * @param o                       the object
     * @param context                 the action context
     * @param throwPropertyExceptions boolean which tells whether it should throw exceptions for
     *                                problems setting the properties
     */
    public static void setProperties(Map props, Object o, Map context, boolean throwPropertyExceptions) {
        if (props == null) {
            return;
        }

        Ognl.setTypeConverter(context, XWorkConverter.getInstance());

        Object oldRoot = Ognl.getRoot(context);
        Ognl.setRoot(context, o);

        for (Iterator iterator = props.entrySet().iterator();
             iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String expression = (String) entry.getKey();

            internalSetProperty(expression, entry.getValue(), o, context, throwPropertyExceptions);
        }

        Ognl.setRoot(context, oldRoot);
    }

    /**
     * Sets the properties on the object using the default context, defaulting to not throwing
     * exceptions for problems setting the properties.
     *
     * @param properties
     * @param o
     */
    public static void setProperties(Map properties, Object o) {
        setProperties(properties, o, false);
    }

    /**
     * Sets the properties on the object using the default context.
     *
     * @param properties              the property map to set on the object
     * @param o                       the object to set the properties into
     * @param throwPropertyExceptions boolean which tells whether it should throw exceptions for
     *                                problems setting the properties
     */
    public static void setProperties(Map properties, Object o, boolean throwPropertyExceptions) {
        Map context = Ognl.createDefaultContext(o);
        setProperties(properties, o, context, throwPropertyExceptions);
    }

    /**
     * Sets the named property to the supplied value on the Object, defaults to not throwing
     * property exceptions.
     *
     * @param name    the name of the property to be set
     * @param value   the value to set into the named property
     * @param o       the object upon which to set the property
     * @param context the context which may include the TypeConverter
     */
    public static void setProperty(String name, Object value, Object o, Map context) {
        setProperty(name, value, o, context, false);
    }

    /**
     * Sets the named property to the supplied value on the Object.
     *
     * @param name                    the name of the property to be set
     * @param value                   the value to set into the named property
     * @param o                       the object upon which to set the property
     * @param context                 the context which may include the TypeConverter
     * @param throwPropertyExceptions boolean which tells whether it should throw exceptions for
     *                                problems setting the property
     */
    public static void setProperty(String name, Object value, Object o, Map context, boolean throwPropertyExceptions) {
        Ognl.setTypeConverter(context, XWorkConverter.getInstance());

        Object oldRoot = Ognl.getRoot(context);
        Ognl.setRoot(context, o);

        internalSetProperty(name, value, o, context, throwPropertyExceptions);

        Ognl.setRoot(context, oldRoot);
    }

    /**
     * Looks for the real target with the specified property given a root Object which may be a
     * CompoundRoot.
     *
     * @return the real target or null if no object can be found with the specified property
     */
    public static Object getRealTarget(String property, Map context, Object root) throws OgnlException {
        //special keyword, they must be cutting the stack
        if ("top".equals(property)) {
            return root;
        }

        if (root instanceof CompoundRoot) {
            // find real target
            CompoundRoot cr = (CompoundRoot) root;

            try {
                for (Iterator iterator = cr.iterator(); iterator.hasNext();) {
                    Object target = iterator.next();

                    if (
                            OgnlRuntime.hasSetProperty((OgnlContext) context, target, property)
                                    ||
                                    OgnlRuntime.hasGetProperty((OgnlContext) context, target, property)
                                    ||
                                    OgnlRuntime.getIndexedPropertyType((OgnlContext) context, target.getClass(), property) != OgnlRuntime.INDEXED_PROPERTY_NONE
                            ) {
                        return target;
                    }
                }
            } catch (IntrospectionException ex) {
                throw new OgnlException("Cannot figure out real target class", ex);
            }

            return null;
        }

        return root;
    }


    /**
     * Wrapper to use OGNL to set the property on target <code>root</code> based on
     * OGNL expression <code>name</code> with the value as <code>value</code>, encapsulating if the
     * expression were to be parsed or compiled depending on {@link #isUseOgnlEnhancement()}.
     * 
     * @param name
     * @param context
     * @param root
     * @param value
     */
    public static void setValue(String name, Map context, Object root, Object value) throws OgnlException {
        if (isUseOgnlEnhancement()) {
            try {
                Node node = Ognl.compileExpression((OgnlContext) context, root, name);
                node.getAccessor().set((OgnlContext) context, root, value);
                return;
            }
            catch(Exception e) {
                log.warn("unable to set value using OGNL expression compilation mode, falling back to expression parsing", e);
            }
        }
        Ognl.setValue(compile(name), context, root, value);
    }

    /**
     * Wrapper to use OGNL to get the property on target <code>root</code> based on the OGNL expression
     * <code>expression</code> encapsulating if the expression were to be parsed or compiled depending on
     * {@link #isUseOgnlEnhancement()}. 
     *
     * @param name
     * @param context
     * @param root
     * @return
     * @throws OgnlException
     */
    public static Object getValue(String name, Map context, Object root) throws OgnlException {
        if (isUseOgnlEnhancement()) {
            try {
                Node node = Ognl.compileExpression((OgnlContext)context, root, name);
                Object result = node.getAccessor().get((OgnlContext)context, root);

                // If we can't get the value with Ognl Enhancement, let's fall back to the default
                // Ognl without-Enhancement, cause
                //       node.getAccessor().get(...)
                // only applies to expression that follow JavaBeans spec. that's how it works, so
                // expression like 'getText(...)' will return null in this case as it's not following
                // JavaBeans spec.
                if (result != null) {
                    return result;
                }                 
            }
            catch(Exception e) {
                log.warn("unable to get value using OGNL expression compilation mode, falling back to expression parsing", e); 
            }
        }
        return Ognl.getValue(compile(name), context, root);
    }

    public static Object getValue(String name, Map context, Object root, Class resultType) throws OgnlException {
        if (isUseOgnlEnhancement()) {
            try {
                Node node = Ognl.compileExpression((OgnlContext)context, root, name);
                return node.getAccessor().get((OgnlContext)context, root);
            }
            catch(Exception e) {
                log.warn("unable to get value using OGNL expression compilation mode, falling back to expression parsing", e);
            }
        }
        return Ognl.getValue(compile(name), context, root, resultType);
    }

    /**
     * Parse an ognl expression specified as <code>expression</code>, cache the parsed result for
     * better response next round.
     * @param expression
     * @return Object
     * @throws OgnlException
     */
    public static Object compile(String expression) throws OgnlException {
        synchronized (parsedExpressions) {
            Object o = parsedExpressions.get(expression);

            if (o == null) {
                o = Ognl.parseExpression(expression);
                parsedExpressions.put(expression, o);
            }

            return o;
        }
    }

    /**
     * Copies the properties in the object "from" and sets them in the object "to"
     * using specified type converter, or {@link com.opensymphony.xwork.util.XWorkConverter} if none
     * is specified.
     *
     * @param from       the source object
     * @param to         the target object
     * @param context    the action context we're running under
     * @param exclusions collection of method names to excluded from copying ( can be null)
     * @param inclusions collection of method names to included copying  (can be null)
     *                   note if exclusions AND inclusions are supplied and not null nothing will get copied.
     */
    public static void copy(Object from, Object to, Map context, Collection exclusions, Collection inclusions) {
        if (from == null || to == null) {
            log.warn("Attempting to copy from or to a null source. This is illegal and is bein skipped. This may be due to an error in an OGNL expression, action chaining, or some other event.");

            return;
        }

        Map contextFrom = Ognl.createDefaultContext(from);
        Ognl.setTypeConverter(contextFrom, XWorkConverter.getInstance());
        Map contextTo = Ognl.createDefaultContext(to);
        Ognl.setTypeConverter(contextTo, XWorkConverter.getInstance());

        PropertyDescriptor[] fromPds;
        PropertyDescriptor[] toPds;

        try {
            fromPds = getPropertyDescriptors(from);
            toPds = getPropertyDescriptors(to);
        } catch (IntrospectionException e) {
            log.error("An error occured", e);

            return;
        }

        Map toPdHash = new HashMap();

        for (int i = 0; i < toPds.length; i++) {
            PropertyDescriptor toPd = toPds[i];
            toPdHash.put(toPd.getName(), toPd);
        }

        for (int i = 0; i < fromPds.length; i++) {
            PropertyDescriptor fromPd = fromPds[i];
            if (fromPd.getReadMethod() != null) {
                boolean copy = true;
                if (exclusions != null && exclusions.contains(fromPd.getName())) {
                    copy = false;
                } else if (inclusions != null && !inclusions.contains(fromPd.getName())) {
                    copy = false;
                }

                if (copy == true) {
                    PropertyDescriptor toPd = (PropertyDescriptor) toPdHash.get(fromPd.getName());
                    if ((toPd != null) && (toPd.getWriteMethod() != null)) {
                        try {

                            // === 1] get value
                            Object value = null;
                            boolean tryExpressionParsing = true;
                            if (isUseOgnlEnhancement()) {
                                try {
                                    Node node = Ognl.compileExpression((OgnlContext)contextFrom, from, fromPd.getName());
                                    value = node.getAccessor().get((OgnlContext)contextFrom, from);
                                    tryExpressionParsing = false;
                                }
                                catch(Exception e) {
                                    // let's try parsing ognl expression instead
                                    tryExpressionParsing = true;
                                    log.warn("unable to get value using OGNL expression compilation mode, falling back to expression parsing", e);
                                }
                            }
                            if (tryExpressionParsing) {
                                Object expr = OgnlUtil.compile(fromPd.getName());
                                value = Ognl.getValue(expr, contextFrom, from);
                            }


                            // === 2] set value
                            tryExpressionParsing = true;
                            if (isUseOgnlEnhancement()) {
                                try {
                                    Node node = Ognl.compileExpression((OgnlContext)contextTo, to, fromPd.getName());
                                    node.getAccessor().set((OgnlContext)contextTo, to, value);
                                    tryExpressionParsing = false;
                                }
                                catch(Exception e) {
                                    // let's try parsing ognl expression instead
                                    tryExpressionParsing = true;
                                    log.warn("unable to set value using OGNL expression compilation mode, falling back to expression parsing", e);
                                }
                            }
                            if (tryExpressionParsing) {
                                Object expr = OgnlUtil.compile(fromPd.getName());
                                Ognl.setValue(expr, contextTo, to, value);
                            }
                        } catch (OgnlException e) {
                            // ignore, this is OK
                        }
                    }

                }

            }

        }
    }


    /**
     * Copies the properties in the object "from" and sets them in the object "to"
     * using specified type converter, or {@link com.opensymphony.xwork.util.XWorkConverter} if none
     * is specified.
     *
     * @param from    the source object
     * @param to      the target object
     * @param context the action context we're running under
     */
    public static void copy(Object from, Object to, Map context) {
        OgnlUtil.copy(from, to, context, null, null);
    }

    /**
     * Get's the java beans property descriptors for the given source.
     * 
     * @param source  the source object.
     * @return  property descriptors.
     * @throws IntrospectionException is thrown if an exception occurs during introspection.
     */
    public static PropertyDescriptor[] getPropertyDescriptors(Object source) throws IntrospectionException {
        BeanInfo beanInfo = getBeanInfo(source);
        return beanInfo.getPropertyDescriptors();
    }

    /**
     * Creates a Map with read properties for the given source object.
     * <p/>
     * If the source object does not have a read property (i.e. write-only) then
     * the property is added to the map with the value <code>here is no read method for property-name</code>.
     * 
     * @param source   the source object.
     * @return  a Map with (key = read property name, value = value of read property).
     * @throws IntrospectionException is thrown if an exception occurs during introspection.
     * @throws OgnlException is thrown by OGNL if the property value could not be retrieved
     */
    public static Map getBeanMap(Object source) throws IntrospectionException, OgnlException {
        Map beanMap = new HashMap();
        Map sourceMap = Ognl.createDefaultContext(source);
        PropertyDescriptor[] propertyDescriptors = getPropertyDescriptors(source);
        for (int i = 0; i < propertyDescriptors.length; i++) {
            PropertyDescriptor propertyDescriptor = propertyDescriptors[i];
            String propertyName = propertyDescriptor.getDisplayName();
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null) {

                Object value = null;
                boolean tryExpressionParsing = true;
                if (isUseOgnlEnhancement()) {
                    try {
                        Node node = Ognl.compileExpression((OgnlContext)sourceMap, source, propertyName);
                        value = node.getAccessor().get((OgnlContext)sourceMap, source);
                        tryExpressionParsing=false;
                    }
                    catch(Exception e) {
                        // let's try expression parsing
                        tryExpressionParsing = true;
                        log.warn("unable to get value using OGNL expression compilation mode, falling back to expression parsing", e);
                    }
                }
                if (tryExpressionParsing) {
                    Object expr = OgnlUtil.compile(propertyName);
                    value = Ognl.getValue(expr, sourceMap, source);
                }
                beanMap.put(propertyName, value);
            } else {
                beanMap.put(propertyName, "There is no read method for " + propertyName);
            }
        }
        return beanMap;
    }

    /**
     * Get's the java bean info for the given source.
     * 
     * @param from  the source object.
     * @return  java bean info.
     * @throws IntrospectionException is thrown if an exception occurs during introspection.
     */
    public static BeanInfo getBeanInfo(Object from) throws IntrospectionException {
        synchronized (beanInfoCache) {
            BeanInfo beanInfo;
            beanInfo = (BeanInfo) beanInfoCache.get(from.getClass());
            if (beanInfo == null) {
                beanInfo = Introspector.getBeanInfo(from.getClass(), Object.class);
                beanInfoCache.put(from.getClass(), beanInfo);
            }
            return beanInfo;
        }
    }

    /**
     * An internal method to set the property whose name is <code>name</code>
     * with value as <code>value</code> into target <code>o</code> with OGNL context
     * map as <code>context</code>. If <code>throwPropertyExceptions</code> is true, we
     * shall throws an exception if we failed to set the property esle we'll just log a
     * warning.
     * 
     * @param name
     * @param value
     * @param o
     * @param context
     * @param throwPropertyExceptions
     */
    static void internalSetProperty(String name, Object value, Object o, Map context, boolean throwPropertyExceptions) {
        try {
            setValue(name, context, o, value);
        } catch (OgnlException e) {
            Throwable reason = e.getReason();
            String msg = "Caught OgnlException while setting property '" + name + "' on type '" + o.getClass().getName() + "'.";
            Throwable exception = (reason == null) ? e : reason;

            if (throwPropertyExceptions) {
                log.error(msg, exception);
                throw new XworkException(msg, exception);
            } else {
                log.warn(msg, exception);
            }
        }
    }

    /**
     * Determine if we should use OGNL (2.7.x) ehhancement feature (compiled expression) instead of
     * the default expression parsing.
     * @return boolean true to use expression compilation false to use expression parsing.
     */
    static boolean isUseOgnlEnhancement() {
        if (useOgnlEnhancement == null) {
            try {
                String value = ConfigurationManager.getConfiguration().getParameter(XWorkConstants.XWORK_USE_OGNL_ENHANCEMENT);
                if (value != null && "true".equalsIgnoreCase(value)) {
                    useOgnlEnhancement = Boolean.TRUE;
                }
                else {
                    useOgnlEnhancement = Boolean.FALSE; // by default, if not specified, its turn off
                }
            }
            catch(Exception e) {
                log.warn("Failed to get xwork parameter ["+ XWorkConstants.XWORK_USE_OGNL_ENHANCEMENT+"] assuming it's false", e);
                useOgnlEnhancement = Boolean.FALSE;
            }
        }
        return useOgnlEnhancement.booleanValue();
    }
}
