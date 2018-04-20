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

package com.example.proftest;

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
    private static final ConcurrentHashMap<String, LongAdder> lifetimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> callCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> callTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> execTimes = new ConcurrentHashMap<>();

    public JMXHelper(Rule rule) {
        super(rule);
    }

    public void registerMBean(String name) {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        synchronized (mbs) {
            try {
                ObjectName oname = new ObjectName(name);
                if (mbs.isRegistered(oname) == false) {
                    mbs.registerMBean(this, oname);
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
        String label = "Byteman statistics";
        List<MBeanAttributeInfo> attributes = new ArrayList<>();

        // Instance count, live instances
        for (Map.Entry<String, LongAdder> e: instances.entrySet()) {
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".instances.count", longName, "Instance count of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".instances.live", longName, "Live instances of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
        }

        // Instance lifetimes
        for (Map.Entry<String, LongAdder> e: lifetimes.entrySet()) {
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".lifetime.average", longName, "Average instance lifetime of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
        }

        // Call counts
        for (Map.Entry<String, LongAdder> e: callCounts.entrySet()) {
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".calls", longName, "Call count of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
        }

        // Call execution times
        for (Map.Entry<String, LongAdder> e: execTimes.entrySet()) {
            ai = new MBeanAttributeInfo(cleanName(e.getKey()) + ".exectime.average", longName, "Average execution time of " + cleanName(e.getKey()), true, false, false);
            attributes.add(ai);
        }

        return new MBeanInfo(clazz, label, attributes.toArray(new MBeanAttributeInfo[attributes.size()]), null, null, null);
    }

    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        if (attribute.endsWith(".instances.count")) {
            String key = attribute.substring(0, attribute.length() - ".instances.count".length());
            return instances.get(key) != null ? instances.get(key).sum() : 0;
        } else if (attribute.endsWith(".instances.live")) {
            String key = attribute.substring(0, attribute.length() - ".instances.live".length());
            long count = instances.get(key) != null ? instances.get(key).sum() : 0;
            long done = instancesDone.get(key) != null ? instancesDone.get(key).sum() : 0;
            return count - done;
        } else if (attribute.endsWith(".lifetime.average")) {
            String key = attribute.substring(0, attribute.length() - ".lifetime.average".length());
            long done = instancesDone.get(key) != null ? instancesDone.get(key).sum() : 0;
            if (done == 0) { return 0; };
            long time = lifetimes.get(key) != null ? lifetimes.get(key).sum() : 0;
            return time / done;
        } else if (attribute.endsWith(".calls")) {
            String key = attribute.substring(0, attribute.length() - ".calls".length());
            return callCounts.get(key) != null ? callCounts.get(key).sum() : 0;
        } else if (attribute.endsWith(".exectime.average")) {
            String key = attribute.substring(0, attribute.length() - ".exectime.average".length());
            long calls = callCounts.get(key) != null ? callCounts.get(key).sum() : 0;
            long time = execTimes.get(key) != null ? execTimes.get(key).sum() : 0;
            return time / calls;
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

    public void recordInstanceCreationTime(String clazz, Object obj) {
        birthdays.put(System.identityHashCode(obj), System.currentTimeMillis());
    }

    public void recordInstanceLifetime(String clazz, Object obj) {
        int id = System.identityHashCode(obj);
        long lifetime = System.currentTimeMillis() - birthdays.get(id);
        lifetimes.computeIfAbsent(clazz, k -> new LongAdder()).add(lifetime);
        instancesDone.computeIfAbsent(clazz, k -> new LongAdder()).increment();
        birthdays.remove(id);
    }

    public void incrementMethodCallCount(String clazz, String method) {
        callCounts.computeIfAbsent(cleanName(clazz + sep + method), k -> new LongAdder()).increment();
    }

    public void recordMethodCallTime(String clazz, String method) {
        callTimes.put(Thread.currentThread().getId(), System.currentTimeMillis());
    }

    public void recordMethodExecTime(String clazz, String method) {
        long id = Thread.currentThread().getId();
        long exectime = System.currentTimeMillis() - callTimes.get(id);
        execTimes.computeIfAbsent(cleanName(clazz + sep + method), k -> new LongAdder()).add(exectime);
        callTimes.remove(id);
    }
}
