/**
\mainpage The ExoPlanet projects documentation

\section int Indroduction

ExoPlanet is an imaginary planetary exploration mission related to the thesis
"Concept and Simulation for Autonomous Navigation of Planetary Rovers".

Based on this mission, a navigation concept was presented for an autonomous exploration
strategy. To demonstrate this concept a simulation platform was developed including a
terrain rendering module and the navigation algorithm based on simulation visual sensors.

\section desc Project description

The application is splitted into two sub projects:
- the ExoSim project is about the 3D terrain visualization using the OGRE Library,
- the ExoPath project implements a client for the simulated sensor data to run navigation and path planning tasks based on the Player robot server and the Eclipse RCP.

\subsection exopath ExoPath

The ExoSim application written in Java to provide the navigation algorithms and a GUI to monitor the exploration task. The connection to the
simulation component is realized by a Player client/server link.


The client package provides the classes needed for the player connection.

<table width="100%">
  <tr><td width="350" class="indexkey"><a class="el" href="a00002.html">exopath::client::PlayerTask</a></td><td class="indexvalue">This class provides the client connection, initialization, the interface management, data polling and notification to the task listeners </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00001.html">exopath::client::ConfigDataInterface</a></td><td class="indexvalue">A custom Player interfaces to request some general configuration data (like map dimensions) </td></tr>
</table>

The nav package provides the navigation algorithm and related classes (like for the exploratio tree).

<table width="100%">
  <tr><td width="350" class="indexkey"><a class="el" href="a00006.html">exopath::nav::NavigationTask</a></td><td class="indexvalue">The class provides the navigation algorithm for the ExoPlanet exploration strategy </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00004.html">exopath::nav::ExplorationTree</a></td><td class="indexvalue">The <a class="el" href="a00004.html" title="The ExplorationTree data type.">ExplorationTree</a> data type </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00003.html">exopath::nav::DijkstraPathFinder</a></td><td class="indexvalue">A path finder implementation that uses the Dijkstra algorithm to determine a path </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00005.html">exopath::nav::Map</a></td><td class="indexvalue">The terrain map for the rover environment </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00007.html">exopath::nav::NavigationTask::Position</a></td><td class="indexvalue">A descriptoin for position related data </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00008.html">exopath::nav::NavigationTask::TreeNode</a></td><td class="indexvalue">The exploration tree nodes (containing the position, mean area radius and the route to the next exploration point </td></tr>
</table>

The ui package provides some GUI elements e.g. to show a (zoomable) map or the rover images

<table width="100%">
  <tr><td width="350" class="indexkey"><a class="el" href="a00018.html">exopath::ui::MapView</a></td><td class="indexvalue">The map view part which contains a map image canvas to display a map of the whole area within the GUI </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00019.html">exopath::ui::NavView</a></td><td class="indexvalue">The navigation view part which contains a image canvas to display the navigation map image (the results of the path planner task) within the GUI </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00016.html">exopath::ui::CamView</a></td><td class="indexvalue">The camera view part which contains a camera canvas to display the rover camera image within the GUI </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00020.html">exopath::ui::SkyCamView</a></td><td class="indexvalue">The camera view part which contains a camera canvas to display the sky camera image within the GUI </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00015.html">exopath::ui::CameraCanvas</a></td><td class="indexvalue">An extended <a class="el" href="a00017.html" title="A GUI part (SWT Canvas), which can be extended to display images within views.">ImageCanvas</a> which creates a image from the Player camera data structure </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00017.html">exopath::ui::ImageCanvas</a></td><td class="indexvalue">A GUI part (SWT Canvas), which can be extended to display images within views </td></tr>
</table>

The rcp package provides all classes needed to create a rich client platform application.

<table width="100%">
  <tr><td width="350" class="indexkey"><a class="el" href="a00009.html">exopath::rcp::Activator</a></td><td class="indexvalue">The activator class controls the plug-in life cycle </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00010.html">exopath::rcp::Application</a></td><td class="indexvalue">This class controls all aspects of the application's execution </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00011.html">exopath::rcp::ApplicationActionBarAdvisor</a></td><td class="indexvalue">An action bar advisor is responsible for creating, adding, and disposing of the actions added to a workbench window </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00012.html">exopath::rcp::ApplicationWorkbenchAdvisor</a></td><td class="indexvalue">A subclass of <code>WorkbenchAdvisor</code> to configure the workbench to suit the needs of the particular application </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00013.html">exopath::rcp::ApplicationWorkbenchWindowAdvisor</a></td><td class="indexvalue">The workbench window advisor object is created in response to a workbench window being created (one per window), and is used to configure the window </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00014.html">exopath::rcp::Perspective</a></td><td class="indexvalue">When a new page is created in the workbench a perspective is used to define the initial page layout (e.g </td></tr>
</table>


\subsection exosim ExoSim

The ExoSim component is written in C++ and provides the terrain modelling and rendering routines to simulate a planetary rover and its sensory input.
This application can run as stand alone executable for interactive controll or as a Player device plug-in.

<table width="100%">
  <tr><td width="350" class="indexkey"><a class="el" href="a00021.html">ExoSimApp</a></td><td class="indexvalue">The ExoSim application class as main component to create a planetary landscape </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00022.html">ExoSimBase</a></td><td class="indexvalue">An extendable base class for the ExoSim application </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00023.html">ExoSimCamMgr</a></td><td class="indexvalue">The camera manager class to provide move or rotate commands or to switch between different perspectives </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00024.html">ExoSimControl</a></td><td class="indexvalue">The application control class for move commands and data access (when app is running as plug-in) </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00025.html">ExoSimDriver</a></td><td class="indexvalue">This is the Player driver instance which is loades as dynamic library plug-in and controls the ExoSim component </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00026.html">ExoSimTerrainMat</a></td><td class="indexvalue">This is a custom terrain material generator extended from the default TerrainMaterialGeneratorA to provide shader scripts to compute deph maps and to modify the visual rendering results (disable specular reflections or use the ortho image for extra shading) </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00027.html">ExoSimTerrainMat::ShaderProfile</a></td><td class="indexvalue">A custom shader profile to change the used CG shader script generator </td></tr>
  <tr><td width="350" class="indexkey"><a class="el" href="a00028.html">ExoSimTerrainMat::ShaderProfile::ShaderHelperCg</a></td><td class="indexvalue">The overwritten helper class for the CG shader script creation </td></tr>
</table>

*/