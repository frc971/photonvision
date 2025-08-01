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

package org.photonvision.vision.processes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.photonvision.common.configuration.ConfigManager;
import org.photonvision.common.util.TestUtils;
import org.photonvision.vision.pipeline.DriverModePipelineSettings;
import org.photonvision.vision.pipeline.PipelineType;

public class PipelineManagerTest {
  @BeforeAll
  public static void init() {
    TestUtils.loadLibraries();
  }

  @Test
  public void testUniqueName() {
    PipelineManager manager = new PipelineManager(new DriverModePipelineSettings(), List.of(), -1);
    manager.addPipeline(PipelineType.Reflective, "Another");

    // We now have ["New Pipeline", "Another"]
    // After we duplicate 0 and 1, we expect ["New Pipeline", "Another", "New Pipeline (1)",
    // "Another (1)"]
    manager.duplicatePipeline(0);
    manager.duplicatePipeline(1);

    // Should add "Another (2)"
    manager.duplicatePipeline(3);
    // Should add "Another (3)
    manager.duplicatePipeline(3);
    // Should add "Another (4)
    manager.duplicatePipeline(1);

    // Should add "Another (5)" through "Another (15)"
    for (int i = 5; i < 15; i++) {
      manager.duplicatePipeline(1);
    }

    var nicks = manager.getPipelineNicknames();
    var expected =
        new ArrayList<>(List.of("New Pipeline", "Another", "New Pipeline (1)", "Another (1)"));
    for (int i = 2; i < 15; i++) {
      expected.add("Another (" + i + ")");
    }
    assertEquals(expected, nicks);
  }

  @Test
  public void testChangeType() {
    // hack since we try to publish to datachangeservice
    ConfigManager.getInstance().load();

    PipelineManager manager = new PipelineManager(new DriverModePipelineSettings(), List.of(), -1);
    // add a reflective pipeline
    manager.addPipeline(PipelineType.Reflective, "Another");
    manager.setIndex(0);
    manager.getCurrentPipeline();
    // and change
    manager.changePipelineType(PipelineType.Aruco.baseIndex);
  }
}
