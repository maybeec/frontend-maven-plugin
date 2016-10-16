assert new File(basedir, 'target/.buildenv/node').exists() : "Node was not installed in the custom install directory";
assert new File(basedir, 'node_modules').exists() : "Node modules were not installed in the base directory";
assert new File(basedir, 'node_modules/less/package.json').exists() : "Less dependency has not been installed successfully";

import org.codehaus.plexus.util.FileUtils;

String buildLog = FileUtils.fileRead(new File(basedir, 'build.log'));

assert buildLog.contains('BUILD SUCCESS') : 'build was not successful'