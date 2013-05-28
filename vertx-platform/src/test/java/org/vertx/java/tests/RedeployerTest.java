package org.vertx.java.tests;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.Deployment;
import org.vertx.java.platform.impl.ModuleIdentifier;
import org.vertx.java.platform.impl.ModuleReloader;
import org.vertx.java.platform.impl.Redeployer;
import org.vertx.java.testframework.TestBase;
import org.vertx.java.testframework.TestUtils;

import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RedeployerTest extends TestBase {

  private static final Logger log = LoggerFactory.getLogger(RedeployerTest.class);

  TestReloader reloader;
  File modRoot;
  Redeployer red;
  String modName = "io.vertx~my-mod~1.0";
  File modDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    reloader = new TestReloader();
    modRoot = new File("reloader-test-mods");
    modRoot.mkdir();
    modDir = createModDir(modName);
    createFile(modDir, "foo.js", TestUtils.randomAlphaString(1000));
    red = new Redeployer(vertx, modRoot, reloader);
  }

  protected void tearDown() throws Exception {
    red.close();
    vertx.fileSystem().deleteSync(modRoot.getAbsolutePath(), true);
    super.tearDown();
  }

  public void testCreateFile() throws Exception {
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    createFile(modDir, "blah.txt", TestUtils.randomAlphaString(1000));
    waitReload(dep);
  }

  public void testModifyFile() throws Exception {
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    modifyFile(modDir, "blah.txt");
    waitReload(dep);
  }

  public void testDeleteFile() throws Exception {
    createFile(modDir, "blah.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    deleteFile(modDir, "blah.txt");
    waitReload(dep);
  }

  public void testCreateDirectory() throws Exception {
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    createDirectory(modDir, "some-dir");
    waitReload(dep);
  }

  public void testCreateFileInSubDirectory() throws Exception {
    createDirectory(modDir, "some-dir");
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    File subDir = new File(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    waitReload(dep);
  }

  public void testDeleteFileInSubDirectory() throws Exception {
    createDirectory(modDir, "some-dir");
    File subDir = new File(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    deleteFile(subDir, "bar.txt");
    waitReload(dep);
  }

  public void testModifyFileInSubDirectory() throws Exception {
    createDirectory(modDir, "some-dir");
    File subDir = new File(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    modifyFile(subDir, "bar.txt");
    waitReload(dep);
  }

  public void testDeleteSubDir() throws Exception {
    createDirectory(modDir, "some-dir");
    File subDir = new File(modDir, "some-dir");
    createFile(subDir, "bar.txt", TestUtils.randomAlphaString(1000));
    Deployment dep = createDeployment("dep1", null);
    red.moduleDeployed(dep);
    Thread.sleep(500);
    vertx.fileSystem().deleteSync(subDir.getAbsolutePath(), true);
    waitReload(dep);
  }

  public void testReloadMultipleDeps() throws Exception {
    createModDir("other-mod");
    Deployment dep1 = createDeployment("dep1", null);
    red.moduleDeployed(dep1);
    Deployment dep2 = createDeployment("dep2", null);
    red.moduleDeployed(dep2);
    String otherModName = "io.vertx~other-mod~1.0";
    Deployment dep3 = createDeployment("dep3", otherModName, null);
    createModDir(otherModName);
    red.moduleDeployed(dep3);
    Thread.sleep(500);
    createFile(modDir, "blah.txt", TestUtils.randomAlphaString(1000));
    waitReload(dep1, dep2);
  }

  private File createModDir(String modName) {
    File modDir = new File(modRoot, modName);
    modDir.mkdir();
    return modDir;
  }

  private void createFile(File dir, String fileName, String content) throws Exception {
    File f = new File(dir, fileName);
    vertx.fileSystem().writeFileSync(f.getAbsolutePath(), new Buffer(content));
  }

  private void modifyFile(File dir, String fileName) throws Exception {
    File f = new File(dir, fileName);
    FileWriter fw = new FileWriter(f, true);
    fw.write(TestUtils.randomAlphaString(500));
    fw.close();
  }

  private void deleteFile(File dir, String fileName) throws Exception {
    File f = new File(dir, fileName);
    f.delete();
  }

  private void createDirectory(File dir, String dirName) throws Exception {
    File f = new File(dir, dirName);
    vertx.fileSystem().mkdirSync(f.getAbsolutePath());
  }

  private void waitReload(Deployment... deps) throws Exception {
    Set<Deployment> set = new HashSet<>();
    for (Deployment dep: deps) {
      set.add(dep);
    }
    reloader.waitReload(set);
  }

  class TestReloader implements ModuleReloader {

    Set<Deployment> reloaded = new ConcurrentHashSet<>();
    CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void reloadModules(Set<Deployment> deps) {
      reloaded.addAll(deps);
      latch.countDown();
    }

    void waitReload(Set<Deployment> deps) throws Exception {
      if (!reloaded.isEmpty()) {
        checkDeps(deps);
      } else {
        if (!latch.await(30000, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Time out");
        }
        checkDeps(deps);
      }
    }

    private void checkDeps(Set<Deployment> deps) {
      assertEquals(deps.size(), reloaded.size());
      for (Deployment dep: deps) {
        assertTrue(reloaded.contains(dep));
      }
    }
  }

  private Deployment createDeployment(String name, String parentName) {
    return createDeployment(name, modName, parentName);
  }

  private Deployment createDeployment(String name, String modName, String parentName) {
     return new Deployment(name, null, new ModuleIdentifier(modName), 1, null, null, null, parentName, null, true);
  }
}
