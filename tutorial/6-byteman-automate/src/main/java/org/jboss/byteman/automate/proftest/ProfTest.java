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

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

class TestUnit implements Runnable {
    private int lifetime;
    private static AtomicInteger countA = new AtomicInteger(0);
    private static AtomicInteger countB = new AtomicInteger(0);
    private static AtomicInteger countC = new AtomicInteger(0);
    private static AtomicInteger instances = new AtomicInteger(0);
    private static AtomicInteger allLife = new AtomicInteger(0);

    public TestUnit(int lifetime) {
        this.lifetime = lifetime;
        instances.incrementAndGet();
        allLife.addAndGet(lifetime);
    }

    public void run() {
        int i = 0;
        while (lifetime-- > 0) {
            i++;
            try {
                Thread.sleep(1000);
            } catch (Exception ex) { ex.printStackTrace(); }
            if (i % 10 == 0) {
                c();
            } else if (i % 4 == 0) {
                b(0);
            } else if (i % 1 == 0) {
                a(0, 0);
            }
        }
    }

    public static void printStats() {
        System.out.print("ProfTest statistics [APP] - ");
        System.out.print(new java.sql.Timestamp(System.currentTimeMillis()));
        System.out.println(":\n");

        System.out.println("Objects instantiated from TestUnit: " + instances.get());
        System.out.println();

        System.out.println("Calls to method a: " + countA.get());
        System.out.println("Calls to method b: " + countB.get());
        System.out.println("Calls to method c: " + countC.get());
        System.out.println();

        int average_lifetime = Math.round((float)allLife.get() / instances.get());
        System.out.println("Average lifetime: " + average_lifetime);
        System.out.println();
    }

    private void a(int i, long l) {
        countA.incrementAndGet();
    }

    private void b(int i) {
        countB.incrementAndGet();
    }

    private void c() {
        countC.incrementAndGet();
    }
}

public class ProfTest {
    public static void main(String[] args) {
        int i = 0;
        while (true) {
            i++;
            try {
                Thread.sleep(1000);
            } catch (Exception ex) { ex.printStackTrace(); }

            int lifetime = ThreadLocalRandom.current().nextInt(1, 21);
            Thread t = new Thread(new TestUnit(lifetime));
            t.start();

            if (i % 10 == 0) {
                TestUnit.printStats();
            }
        }
    }
}
