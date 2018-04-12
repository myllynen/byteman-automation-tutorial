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

import java.io.FileOutputStream;
import java.io.PrintWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.byteman.rule.Rule;
import org.jboss.byteman.rule.helper.Helper;

public class JSONHelper extends Helper {
    private static AtomicInteger countA = new AtomicInteger(0);
    private static AtomicInteger countB = new AtomicInteger(0);
    private static AtomicInteger countC = new AtomicInteger(0);
    private static AtomicInteger instances = new AtomicInteger(0);
    private static AtomicInteger instancesDone = new AtomicInteger(0);
    private static AtomicInteger allLife = new AtomicInteger(0);

    private static Map<Integer, Long> map = new ConcurrentHashMap<Integer, Long>();

    private static String JSONFILE = "data.json";
    private static String TEMPFILE = "temp.json";

    public JSONHelper(Rule rule) {
        super(rule);
    }

    public void writeJSON() {
        try {
            PrintWriter writer = new PrintWriter(new FileOutputStream(TEMPFILE, false));
            writer.print("{\n");

            writer.print("  \"instances\": " + instances.get() + ",\n");

            writer.print("  \"count_a\": " + countA.get() + ",\n");
            writer.print("  \"count_b\": " + countB.get() + ",\n");
            writer.print("  \"count_c\": " + countC.get() + ",\n");

            int average_lifetime = Math.round((float)allLife.get() / instancesDone.get());
            writer.print("  \"average_lifetime\": " +  average_lifetime + "\n");

            writer.print("}\n");

            writer.close();

            Path src = Paths.get(TEMPFILE);
            Path dst = Paths.get(JSONFILE);
            Files.move(src, dst, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) { ex.printStackTrace(); }
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
