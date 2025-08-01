/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.common.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class NetworkConfigTest {
  @Test
  public void testSerialization() throws IOException {
    var mapper = new ObjectMapper();
    var path = Path.of("netTest.json");
    mapper.writeValue(path.toFile(), new NetworkConfig());
    assertDoesNotThrow(() -> mapper.readValue(path.toFile(), NetworkConfig.class));
    new File("netTest.json").delete();
  }

  @Test
  public void testDeserializeTeamNumberOrNtServerAddress() {
    {
      var folder = Path.of("test-resources/network-old-team-number");
      var configMgr = new ConfigManager(folder, new LegacyConfigProvider(folder));
      configMgr.load();
      assertEquals("9999", configMgr.getConfig().getNetworkConfig().ntServerAddress);
    }
    {
      var folder = Path.of("test-resources/network-new-team-number");
      var configMgr = new ConfigManager(folder, new LegacyConfigProvider(folder));
      configMgr.load();
      assertEquals("9999", configMgr.getConfig().getNetworkConfig().ntServerAddress);
    }
  }
}
