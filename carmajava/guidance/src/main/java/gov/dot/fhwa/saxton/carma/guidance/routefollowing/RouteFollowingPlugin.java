/*
 * Copyright (C) 2018 LEIDOS.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package gov.dot.fhwa.saxton.carma.guidance.routefollowing;

import gov.dot.fhwa.saxton.carma.guidance.ManeuverPlanner;
import gov.dot.fhwa.saxton.carma.guidance.arbitrator.TrajectoryPlanningResponse;
import gov.dot.fhwa.saxton.carma.guidance.lanechange.LaneChangePlugin;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.IManeuver;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.ISimpleManeuver;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.LongitudinalManeuver;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.ManeuverType;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.SimpleManeuverFactory;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.SteadySpeed;
import gov.dot.fhwa.saxton.carma.guidance.maneuvers.LaneKeeping;
import gov.dot.fhwa.saxton.carma.guidance.plugins.AbstractPlugin;
import gov.dot.fhwa.saxton.carma.guidance.plugins.IStrategicPlugin;
import gov.dot.fhwa.saxton.carma.guidance.plugins.ITacticalPlugin;
import gov.dot.fhwa.saxton.carma.guidance.plugins.PluginManagementService;
import gov.dot.fhwa.saxton.carma.guidance.plugins.PluginServiceLocator;
import gov.dot.fhwa.saxton.carma.guidance.trajectory.Trajectory;
import gov.dot.fhwa.saxton.carma.guidance.util.RequiredLane;
import gov.dot.fhwa.saxton.carma.guidance.util.RouteService;
import gov.dot.fhwa.saxton.carma.guidance.util.SpeedLimit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;

/**
 * Basic plugin for following necessary lane changes in the currently selected
 * route
 * <p>
 * Delegates planning of the lane change maneuver(s) themselves to the
 * LaneChange plugin by use of the ITacticalPlugin interface.
 */
public class RouteFollowingPlugin extends AbstractPlugin implements IStrategicPlugin {

    private ITacticalPlugin laneChangePlugin;
    private RouteService routeService;
    private double laneChangeRateFactor = 0.75;
    private double laneChangeDelayFactor = 1.5;
    private double laneChangeSafetyFactor = 1.5;
    private double laneChangeNotificationTime = 4.0;

    private static final String LANE_CHANGE_PLUGIN_NAME = "Lane Change Plugin";
    private static final long LONG_SLEEP = 10000;
    private static final double EPSILON = 0.001;

    public RouteFollowingPlugin(PluginServiceLocator psl) {
        super(psl);
        version.setName("Route Following Plugin");
        version.setMajorRevision(1);
        version.setIntermediateRevision(0);
        version.setMinorRevision(0);
    }

    @Override
    public void onInitialize() {
        laneChangeRateFactor = pluginServiceLocator.getParameterSource().getDouble("~lane_change_rate_factor", 0.75);
        laneChangeDelayFactor = pluginServiceLocator.getParameterSource().getDouble("~lane_change_delay_factor", 1.5);
        laneChangeSafetyFactor = pluginServiceLocator.getParameterSource().getDouble("~lane_change_safety_factor", 1.5);
        laneChangeNotificationTime = pluginServiceLocator.getParameterSource()
                .getDouble("~lane_change_notification_time", 4.0);
        log.info("Route Following plugin initialized");
    }

    @Override
    public void onResume() {
        laneChangePlugin = pluginServiceLocator.getPluginManagementService()
                .getTacticalPluginByName(LANE_CHANGE_PLUGIN_NAME);

        if (laneChangePlugin == null) {
            // No lane change plugin was found registered withe the platform, RouteFollowing
            // cannot operate
            throw new RuntimeException(
                    "Route following plugin unable to locate necessary lane change tactical plugin.\nPlease install a lane change plugin.");
        }
        log.info("Route Following Plugin using Lane Change Plugin: " + laneChangePlugin.getVersionInfo());

        routeService = pluginServiceLocator.getRouteService();
        log.info("Route Following plugin resumed");
        setAvailability(true);
    }

    @Override
    public void loop() throws InterruptedException {
        Thread.sleep(LONG_SLEEP);
    }

    @Override
    public void onSuspend() {
        log.info("Route Following plugin suspended");
    }

    @Override
    public void onTerminate() {
        log.info("Route Following plugin terminated");
    }

    private void planLaneKeepingManeuver(Trajectory traj, double startDist, double endDist) {
        log.info(String.format("Planning lane keeping maneuver planning between: [%.02f, %.02f)", startDist, endDist));
        LaneKeeping laneKeepingManeuver = new LaneKeeping(this);
        laneKeepingManeuver.planToTargetDistance(pluginServiceLocator.getManeuverPlanner().getManeuverInputs(),
                pluginServiceLocator.getManeuverPlanner().getGuidanceCommands(), startDist, endDist);
        traj.addManeuver(laneKeepingManeuver);
    }

    @Override
    public TrajectoryPlanningResponse planTrajectory(Trajectory traj, double expectedEntrySpeed) {
        log.info("Route Following Plugin planning trajectory!");
        // TODO: Implement planning logic to handle planning around other lateral
        // maneuvers that may already be planned!
        SortedSet<RequiredLane> requiredLanes = routeService.getRequiredLanesInRange(traj.getStartLocation(),
                traj.getEndLocation());

        RequiredLane laneChangeAfterTraj = routeService.getRequiredLaneAtLocation(traj.getEndLocation());
        if (laneChangeAfterTraj != null) {
            requiredLanes.add(laneChangeAfterTraj); // Handle the end point of the trajectory
        }

        log.info(String.format("Identified %d lane changes in trajectory", requiredLanes.size()));
        String changes = "";
        for (RequiredLane lane : requiredLanes) {
            changes += lane.toString() + ",";
        }
        log.info("Changes: " + changes);

        // Ensure that we have enough space after the current trajectory to complete the
        // NEXT lane change
        if (!requiredLanes.isEmpty()) {
            RequiredLane laneChangeAtTrajEnd = (RequiredLane) requiredLanes.toArray()[requiredLanes.size() - 1];

            if (laneChangeAtTrajEnd.getLocation() > traj.getEndLocation()) {
                double speedLimit = routeService.getSpeedLimitAtLocation(laneChangeAtTrajEnd.getLocation()).getLimit();
                double distanceRequiredForLaneChange = speedLimit * (laneChangeDelayFactor + laneChangeRateFactor
                        + laneChangeSafetyFactor + laneChangeNotificationTime);
                double distanceAvailableForLaneChange = laneChangeAtTrajEnd.getLocation() - traj.getEndLocation();

                if (distanceAvailableForLaneChange < distanceRequiredForLaneChange) {
                    // Tell the arbitrator the next trajectory wont be able to execute the whole
                    // lane change, so roll
                    // that maneuver into this one.
                    log.warn("Next lane change maneuver would be impossible. Requesting longer trajectory to address!");
                    log.info("Requesting trajectory extension to: " + laneChangeAtTrajEnd.getLocation());
                    TrajectoryPlanningResponse requestForLongerTrajectory = new TrajectoryPlanningResponse();
                    requestForLongerTrajectory.requestLongerTrajectory(laneChangeAtTrajEnd.getLocation());
                    return requestForLongerTrajectory;
                }
            }
        }

        // Walk the lane changes required by route and plan maneuvers for each of them.
        for (RequiredLane targetLane : requiredLanes) {
            if (targetLane.getLocation() > traj.getEndLocation()) {
                log.info("Ignoring lane change " + targetLane + " for now, since it's not on the trajectory.");
                continue;
            }
            double speedLimit = routeService.getSpeedLimitAtLocation(targetLane.getLocation()).getLimit();
            double distanceForLaneChange = speedLimit * laneChangeDelayFactor + speedLimit * laneChangeRateFactor
                    + speedLimit * laneChangeSafetyFactor;

            double laneChangeStartLocation = targetLane.getLocation() - distanceForLaneChange;

            if (laneChangeStartLocation < traj.getStartLocation()) {
                // Should not be the case ever but we'll give it a best effort attempt anyway
                laneChangeStartLocation = traj.getStartLocation();
            }

            // Check to see if we're done with the previous lane change
            IManeuver lateralManeuverAtStartLocation = traj.getManeuverAt(laneChangeStartLocation,
                    ManeuverType.LATERAL);
            if (lateralManeuverAtStartLocation != null) {
                // Plan our new maneuver right after that one, giving a best effort attempt
                laneChangeStartLocation = lateralManeuverAtStartLocation.getEndDistance();
            }

            double laneChangeEndLocation = laneChangeStartLocation + distanceForLaneChange;
            if (laneChangeEndLocation > traj.getEndLocation()) {
                // If we don't have enough room in the trajectory to execute our "best effort"
                // attempt, request a longer trajectory
                log.warn("Current lane change maneuver would be impossible. Requesting longer trajectory to address!");
                log.info("Requesting trajectory extension to: " + laneChangeEndLocation + speedLimit * 0.5);
                TrajectoryPlanningResponse requestForLongerTrajectory = new TrajectoryPlanningResponse();
                // Request enough space to execute the maneuver plus a little bit of breathing
                // room
                requestForLongerTrajectory.requestLongerTrajectory(laneChangeEndLocation + speedLimit * 0.5);
                return requestForLongerTrajectory;
            }

            // Check for existing maneuvers in the middle of the to-be-planned maneuver
            List<IManeuver> mvrsAtStart = traj.getManeuversAt(laneChangeStartLocation);
            List<IManeuver> mvrsAtEnd = traj.getManeuversAt(laneChangeEndLocation);

            double endOfMvrsAtStart = laneChangeStartLocation;
            for (IManeuver m : mvrsAtStart) {
                if (m.getEndDistance() > endOfMvrsAtStart) {
                    endOfMvrsAtStart = m.getEndDistance();
                }
            }
            // Need to update if additional maneuver types are introduced
            IManeuver nextLonMvr = traj.getNextManeuverAfter(endOfMvrsAtStart, ManeuverType.LONGITUDINAL);
            IManeuver nextLatMvr = traj.getNextManeuverAfter(endOfMvrsAtStart, ManeuverType.LATERAL);
            IManeuver nextCmplxMvr = traj.getNextManeuverAfter(endOfMvrsAtStart, ManeuverType.COMPLEX);

            double startOfMvrsAtEnd = laneChangeEndLocation;
            for (IManeuver m : mvrsAtEnd) {
                if (m.getEndDistance() < startOfMvrsAtEnd) {
                    startOfMvrsAtEnd = m.getStartDistance();
                }
            }

            boolean maneuverInMiddleOfLaneChange = (nextLonMvr != null && nextLonMvr.getStartDistance() < startOfMvrsAtEnd)
                    || (nextLatMvr != null && nextLatMvr.getStartDistance() < startOfMvrsAtEnd)
                    || (nextCmplxMvr != null && nextCmplxMvr.getStartDistance() < startOfMvrsAtEnd);
            boolean maneuverAtStartOfLaneChange = !mvrsAtStart.isEmpty();

            // Compute the distance required and command the lane change plugin to plan
            double startSpeedLimit = routeService.getSpeedLimitAtLocation(laneChangeStartLocation).getLimit();
            double endSpeedLimit = routeService.getSpeedLimitAtLocation(laneChangeEndLocation).getLimit();
            if (maneuverAtStartOfLaneChange || maneuverInMiddleOfLaneChange) {
                log.warn(String.format(
                        "Unable to delegate to Lane Change Plugin with params = {laneId=%d,startLimit=%.02f,endLimit=%.02f,start=%.02f,end=%.02f}, due to maneuvers planned in space needed!",
                        targetLane.getLaneId(), startSpeedLimit, endSpeedLimit, laneChangeStartLocation,
                        laneChangeEndLocation));
            } else {
                log.info(String.format(
                        "Delegating to Lane Change Plugin with params = {laneId=%d,startLimit=%.02f,endLimit=%.02f,start=%.02f,end=%.02f}",
                        targetLane.getLaneId(), startSpeedLimit, endSpeedLimit, laneChangeStartLocation,
                        laneChangeEndLocation));
                ((LaneChangePlugin) laneChangePlugin).setLaneChangeParameters(targetLane.getLaneId(), startSpeedLimit,
                        endSpeedLimit);
                laneChangePlugin.planSubtrajectory(traj, laneChangeStartLocation, laneChangeEndLocation);
            }
        }

        // Now that all lane changes have been planned, backfill with lane keeping
        double windowStart = traj.findEarliestLateralWindowOfSize(EPSILON);
        while (windowStart != -1) {
            IManeuver m = traj.getNextManeuverAfter(windowStart, ManeuverType.LATERAL);
            double windowEnd = (m != null ? m.getStartDistance() : traj.getEndLocation());

            // Ensure we don't try to plan over a complex maneuver at the end of a
            // trajectory
            if (traj.getComplexManeuver() != null) {
                if (windowEnd > traj.getComplexManeuver().getStartDistance()) {
                    // Cap it to the end of the complex maneuver and then finish planning
                    planLaneKeepingManeuver(traj, windowStart, traj.getComplexManeuver().getStartDistance());
                    break;
                }
            }

            planLaneKeepingManeuver(traj, windowStart, windowEnd);

            windowStart = traj.findEarliestLateralWindowOfSize(EPSILON);
        }

        log.info("Route Following planning success!");
        return new TrajectoryPlanningResponse(); // Success!
    }
}
