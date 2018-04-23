/*
 * Copyright (C) 2018 Marko Myllynen <myllynen@redhat.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is subject to the "Classpath" exception as provided
 * by the authors in the LICENSE file that accompanied this code.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.jboss.byteman.automate.proftool;

import java.lang.management.ManagementFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

public class JMXHelper extends Helper implements DynamicMBean {
    private static final String sep = "#";
    private static final String longName = Long.class.getName();
    private static final ConcurrentHashMap<String, LongAdder> instances = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> instancesDone = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Long> birthdays = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> lifetimesMin = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> lifetimesMax = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> lifetimesTot = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> callCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> callTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> execTimesMin = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> execTimesMax = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> execTimesTot = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> exitExcept = new ConcurrentHashMap<>();

    private static boolean recordInstMinLife = false;
    private static boolean recordInstMaxLife = false;
    private static boolean recordMinExecTime = false;
    private static boolean recordMaxExecTime = false;
    private static boolean recordExitExcept = false;

    public JMXHelper(Rule rule) {
        super(rule);
    }

    public void registerMBean(String name, boolean recordInstMinLife, boolean recordInstMaxLife, boolean recordMinExecTime, boolean recordMaxExecTime, boolean recordExitExcept) {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        synchronized (mbs) {
            try {
                ObjectName oname = new ObjectName(name);
                if (!mbs.isRegistered(oname)) {
                    mbs.registerMBean(this, oname);
                    this.recordInstMinLife = recordInstMinLife;
                    this.recordInstMaxLife = recordInstMaxLife;
                    this.recordMinExecTime = recordMinExecTime;
                    this.recordMaxExecTime = recordMaxExecTime;
                    this.recordExitExcept = recordExitExcept;
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private String cleanName(String key) {
        key = key.replace(" ", "_").replace("(", "_").replace(")", "").replace("<", "").replace(">", "");
        return key.replace("?", "").replace(",", "_").replace(sep, ".").replace("/", "");
    }

    public MBeanInfo getMBeanInfo() {
        MBeanAttributeInfo ai;
        String clazz = getClass().getName();
        String label = "Byteman ProfTool statistics";
        List<MBeanAttributeInfo> attributes = new ArrayList<>();

        // Instance count, live instances
        for (Map.Entry<String, LongAdder> e: instances.entrySet()) {
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".instances.total", longName, "Total instances of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".instances.live", longName, "Live instances of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
        }

        // Instance lifetimes
        for (Map.Entry<String, LongAdder> e: lifetimesTot.entrySet()) {
            if (recordInstMinLife) {
                ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".lifetime.minimum", longName, "Minimum instance lifetime of " + cleanName(e.getKey()), true, false, false);
                attributes.add(ai);
            }
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".lifetime.average", longName, "Average instance lifetime of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
            if (recordInstMaxLife) {
                ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".lifetime.maximum", longName, "Maximum instance lifetime of " + cleanName(e.getKey()), true, false, false);
                attributes.add(ai);
            }
        }

        // Call counts
        for (Map.Entry<String, LongAdder> e: callCounts.entrySet()) {
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".calls", longName, "Call count of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
        }

        // Call execution times
        for (Map.Entry<String, LongAdder> e: execTimesTot.entrySet()) {
            if (recordMinExecTime) {
                ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".exectime.minimum", longName, "Minimum execution time of " + cleanName(e.getKey()), true, false, false);
                attributes.add(ai);
            }
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".exectime.average", longName, "Average execution time of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
            if (recordMaxExecTime) {
                ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".exectime.maximum", longName, "Maximum execution time of " + cleanName(e.getKey()), true, false, false);
                attributes.add(ai);
            }
        }

        // Exits via exceptions
        if (recordExitExcept) {
            for (Map.Entry<String, LongAdder> e: exitExcept.entrySet()) {
                ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".exit.exception", longName, "Exits via exceptions from " + cleanName(e.getKey()), true, false, false);
                attributes.add(ai);
            }
        }

        return new MBeanInfo(clazz, label, attributes.toArray(new MBeanAttributeInfo[attributes.size()]), null, null, null);
    }

    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        if (attribute.endsWith(".instances.total")) {
            String key = attribute.substring(0, attribute.length() - ".instances.total".length());
            return instances.get(key) != null ? instances.get(key).sum() : 0;
        } else if (attribute.endsWith(".instances.live")) {
            String key = attribute.substring(0, attribute.length() - ".instances.live".length());
            long count = instances.get(key) != null ? instances.get(key).sum() : 0;
            long done = instancesDone.get(key) != null ? instancesDone.get(key).sum() : 0;
            return count - done;
        } else if (attribute.endsWith(".lifetime.minimum")) {
            String key = attribute.substring(0, attribute.length() - ".lifetime.average".length());
            return lifetimesMin.get(key);
        } else if (attribute.endsWith(".lifetime.average")) {
            String key = attribute.substring(0, attribute.length() - ".lifetime.average".length());
            long done = instancesDone.get(key) != null ? instancesDone.get(key).sum() : 0;
            if (done == 0) { return 0; };
            long time = lifetimesTot.get(key) != null ? lifetimesTot.get(key).sum() : 0;
            return time / done;
        } else if (attribute.endsWith(".lifetime.maximum")) {
            String key = attribute.substring(0, attribute.length() - ".lifetime.maximum".length());
            return lifetimesMax.get(key);
        } else if (attribute.endsWith(".calls")) {
            String key = attribute.substring(0, attribute.length() - ".calls".length());
            return callCounts.get(key) != null ? callCounts.get(key).sum() : 0;
        } else if (attribute.endsWith(".exectime.minimum")) {
            String key = attribute.substring(0, attribute.length() - ".exectime.minimum".length());
            return execTimesMin.get(key);
        } else if (attribute.endsWith(".exectime.average")) {
            String key = attribute.substring(0, attribute.length() - ".exectime.average".length());
            long calls = callCounts.get(key) != null ? callCounts.get(key).sum() : 0;
            long time = execTimesTot.get(key) != null ? execTimesTot.get(key).sum() : 0;
            return time / calls;
        } else if (attribute.endsWith(".exectime.maximum")) {
            String key = attribute.substring(0, attribute.length() - ".exectime.maximum".length());
            return execTimesMax.get(key);
        } else if (attribute.endsWith(".exit.exception")) {
            String key = attribute.substring(0, attribute.length() - ".exit.exception".length());
            return exitExcept.get(key) != null ? exitExcept.get(key).sum() : 0;
        } else {
            throw new AttributeNotFoundException(attribute);
        }
    }

    public AttributeList getAttributes(String[] attributes) {
        AttributeList list = new AttributeList();
        for (String name: attributes) {
            try {
                Object value = getAttribute(name);
                if (value != null) {
                    list.add(new Attribute(name, value));
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }
        return list;
    }

    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
    }

    public AttributeList setAttributes(AttributeList attributes) {
        return new AttributeList();
    }

    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        return null;
    }

    public void incrementInstanceCount(String clazz) {
        instances.computeIfAbsent(clazz, k -> new LongAdder()).increment();
    }

    public void decrementLiveInstanceCount(String clazz) {
        instancesDone.computeIfAbsent(clazz, k -> new LongAdder()).increment();
    }

    public void recordInstanceCreationTime(String clazz, Object obj) {
        birthdays.put(System.identityHashCode(obj), System.currentTimeMillis());
    }

    public void recordInstanceLifetime(String clazz, Object obj) {
        int id = System.identityHashCode(obj);
        long lifetime = System.currentTimeMillis() - birthdays.get(id);
        lifetimesTot.computeIfAbsent(clazz, k -> new LongAdder()).add(lifetime);
        if (recordInstMinLife) {
            lifetimesMin.putIfAbsent(clazz, lifetime);
            if (lifetime < lifetimesMin.get(clazz)) {
                lifetimesMin.replace(clazz, lifetime);
            }
        }
        if (recordInstMaxLife) {
            lifetimesMax.putIfAbsent(clazz, lifetime);
            if (lifetime > lifetimesMax.get(clazz)) {
                lifetimesMax.replace(clazz, lifetime);
            }
        }
        instancesDone.computeIfAbsent(clazz, k -> new LongAdder()).increment();
        birthdays.remove(id);
    }

    public void incrementMethodCallCount(String clazz, String method) {
        callCounts.computeIfAbsent(cleanName(clazz + sep + method), k -> new LongAdder()).increment();
    }

    public void recordMethodCallTime(String clazz, String method) {
        callTimes.put(clazz + sep + method + Long.toString(Thread.currentThread().getId()), System.currentTimeMillis());
    }

    public void recordMethodExecTime(String clazz, String method) {
        String id = clazz + sep + method + Long.toString(Thread.currentThread().getId());
        long exectime = System.currentTimeMillis() - callTimes.get(id);
        String key = cleanName(clazz + sep + method);
        execTimesTot.computeIfAbsent(key, k -> new LongAdder()).add(exectime);
        if (recordMinExecTime) {
            execTimesMin.putIfAbsent(key, exectime);
            if (exectime < execTimesMin.get(key)) {
                execTimesMin.replace(key, exectime);
            }
        }
        if (recordMaxExecTime) {
            execTimesMax.putIfAbsent(key, exectime);
            if (exectime > execTimesMax.get(key)) {
                execTimesMax.replace(key, exectime);
            }
        }
        callTimes.remove(id);
    }

    public void incrementMethodExitExceptCount(String clazz, String method) {
        exitExcept.computeIfAbsent(cleanName(clazz + sep + method), k -> new LongAdder()).increment();
        if (!callTimes.isEmpty()) {
            recordMethodExecTime(clazz, method);
        }
    }
}
