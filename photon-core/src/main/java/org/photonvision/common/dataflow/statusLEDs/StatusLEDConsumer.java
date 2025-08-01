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

package org.photonvision.common.dataflow.statusLEDs;

import org.photonvision.common.dataflow.CVPipelineResultConsumer;
import org.photonvision.common.hardware.HardwareManager;
import org.photonvision.vision.pipeline.result.CVPipelineResult;

public class StatusLEDConsumer implements CVPipelineResultConsumer {
  private final String uniqueName;

  public StatusLEDConsumer(String uniqueName) {
    this.uniqueName = uniqueName;
  }

  @Override
  public void accept(CVPipelineResult t) {
    HardwareManager.getInstance().setTargetsVisibleStatus(this.uniqueName, t.hasTargets());
  }
}
