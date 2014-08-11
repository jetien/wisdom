/*
 * #%L
 * Wisdom-Framework
 * %%
 * Copyright (C) 2013 - 2014 Wisdom Framework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package org.wisdom.framework.vertx;

import jdk.nashorn.internal.ir.annotations.Ignore;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.impl.DefaultVertxFactory;
import org.vertx.java.core.streams.Pump;

import java.io.*;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class AsyncInputStreamTest {

    CountDownLatch latch;

    Vertx vertx = new DefaultVertxFactory().createVertx();

    @Test
    public void testReadSmallFile() throws FileNotFoundException, InterruptedException {
        latch = new CountDownLatch(1);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        File file = new File("src/test/resources/a_file.txt");
        FileInputStream fis = new FileInputStream(file);
        final AsyncInputStream async = new AsyncInputStream(vertx, Executors.newSingleThreadExecutor(), fis)
                .endHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        assertThat(bos.toString()).startsWith("This is a file.");
                        latch.countDown();
                    }
                });
        vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                async.dataHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer event) {
                        try {
                            bos.write(event.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void testReadSmallFileFromUrl() throws IOException, InterruptedException {
        latch = new CountDownLatch(1);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        File file = new File("src/test/resources/a_file.txt");
        URL url = file.toURI().toURL();
        final AsyncInputStream async = new AsyncInputStream(vertx, Executors.newSingleThreadExecutor(),
                url.openStream())
                .endHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        assertThat(bos.toString()).startsWith("This is a file.");
                        latch.countDown();
                    }
                });
        vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                async.dataHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer buffer) {
                        try {
                            bos.write(buffer.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void testReadMediumFileFromUrl() throws IOException, InterruptedException {
        latch = new CountDownLatch(1);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        File file = new File("src/test/resources/These.pdf");
        URL url = file.toURI().toURL();
        final AsyncInputStream async = new AsyncInputStream(vertx, Executors.newSingleThreadExecutor(),
                url.openStream());
        async.endHandler(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                System.out.println("testing");
                assertThat(async.transferredBytes()).isEqualTo(9280262l);
                try {
                    assertThat(async.isClosed()).isTrue();
                } catch (Exception e) {
                    fail(e.getMessage());
                }
                latch.countDown();
            }
        });
        vertx.runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void event) {
                async.dataHandler(new Handler<Buffer>() {
                    @Override
                    public void handle(Buffer event) {
                        try {
                            bos.write(event.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    @Ignore
    public void testReadMediumFileFromUrlWithPump() throws IOException, InterruptedException {
        final AtomicBoolean hasBeenTested = new AtomicBoolean(false);
        latch = new CountDownLatch(1);
        final String path = "target/junk/These.pdf";
        File file = new File("src/test/resources/These.pdf");
        final URL url = file.toURI().toURL();

        vertx.runOnContext(new Handler<Void>() {

            @Override
            public void handle(Void event) {
                final AsyncInputStream async;
                try {
                    async = new AsyncInputStream(vertx, Executors.newSingleThreadExecutor(),
                            url.openStream());
                } catch (IOException e) {
                    return;
                }
                async.endHandler(new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        System.out.println("testing");
                        assertThat(async.transferredBytes()).isEqualTo(9280262l);
                        File r = new File(path);
                        try {
                            assertThat(async.isClosed()).isTrue();
                        } catch (Exception e) {
                            fail(e.getMessage());
                        }
                        assertThat(r).exists().canRead().isFile();
                        assertThat(r.length()).isEqualTo(9280262l);
                        hasBeenTested.set(true);
                        latch.countDown();
                    }
                });
                File f = new File(path);
                if (f.exists()) {
                    FileUtils.deleteQuietly(f);
                }
                f.getParentFile().mkdirs();
                try {
                    f.createNewFile();
                } catch (IOException e) {
                    return;
                }
                AsyncFile afile = vertx.fileSystem().openSync(path);
                Pump.createPump(async, afile).start();

            }
        });


        latch.await(20, TimeUnit.SECONDS);
        assertThat(hasBeenTested.get()).isTrue();
    }

//    @Test
//    public void testReadVeryLargeFile() throws IOException, InterruptedException {
//        latch = new CountDownLatch(1);
//        File file = new File("/Users/clement/Movies/Escape/Escape.mkv");
//        URL url = file.toURI().toURL();
//        final AsyncInputStream async = new AsyncInputStream(vertx, url.openStream());
//        async.endHandler(new Handler<Void>() {
//            @Override
//            public void handle(Void event) {
//                System.out.println("testing");
//                assertThat(async.transferredBytes()).isEqualTo(4938255463l);
//                latch.countDown();
//            }
//        });
//        async.dataHandler(new Handler<Buffer>() {
//            @Override
//            public void handle(Buffer event) {
//                // Do nothing with this.
//            }
//        });
//        latch.await(10, TimeUnit.SECONDS);
//    }

}