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

package org.jboss.byteman.automate.proftest;

import java.lang.management.ManagementFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static AtomicInteger countA = new AtomicInteger(0);
    private static AtomicInteger countB = new AtomicInteger(0);
    private static AtomicInteger countC = new AtomicInteger(0);
    private static AtomicInteger instances = new AtomicInteger(0);
    private static AtomicInteger instancesDone = new AtomicInteger(0);
    private static AtomicInteger allLife = new AtomicInteger(0);

    private static Map<Integer, Long> map = new ConcurrentHashMap<Integer, Long>();

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

    public MBeanInfo getMBeanInfo() {
        String clazz = getClass().getName();
        String label = "ProfTest statistics";
        MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[5];

        attributes[0] = new MBeanAttributeInfo("instances", "java.lang.Integer", "Objects instantiated from TestUnit", true, false, false);
        attributes[1] = new MBeanAttributeInfo("count_a", "java.lang.Integer", "Calls to method a", true, false, false);
        attributes[2] = new MBeanAttributeInfo("count_b", "java.lang.Integer", "Calls to method b", true, false, false);
        attributes[3] = new MBeanAttributeInfo("count_c", "java.lang.Integer", "Calls to method c", true, false, false);
        attributes[4] = new MBeanAttributeInfo("average_lifetime", "java.lang.Integer", "Average lifetime", true, false, false);

        return new MBeanInfo(clazz, label, attributes, null, null, null);
    }

    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        if (attribute.equals("instances")) {
            return instances.get();
        }

        if (attribute.equals("count_a")) {
            return countA.get();
        }

        if (attribute.equals("count_b")) {
            return countB.get();
        }

        if (attribute.equals("count_c")) {
            return countC.get();
        }

        if (attribute.equals("average_lifetime")) {
            return Math.round((float)allLife.get() / instancesDone.get());
        }

        throw new AttributeNotFoundException(attribute);
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

    public void incrementInstances() {
        instances.incrementAndGet();
    }

    public void incrementCountA() {
        countA.incrementAndGet();
    }

    public void incrementCountB() {
        countB.incrementAndGet();
    }

    public void incrementCountC() {
        countC.incrementAndGet();
    }

    public void recordCreationTime(int id) {
        map.put(id, System.currentTimeMillis());
    }

    public void recordInstanceLifetime(int id) {
        long now = System.currentTimeMillis();
        long then = map.get(id);
        long lifetime = now - then;
        allLife.addAndGet((int)(lifetime / 1000));
        instancesDone.incrementAndGet();
        map.remove(id);
    }
}
