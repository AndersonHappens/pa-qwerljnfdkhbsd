/* Joseph Tate, jgt17
 * Jake Anderson, jta40
 */

package edu.cwru.sepia.agent;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class AstarAgent extends Agent {

    class MapLocation
    {
        public int x, y;
        private MapLocation cameFrom;//added
        private float cost;//added


        public MapLocation(int x, int y, MapLocation cameFrom, float cost)
        {
            this.x = x;
            this.y = y;
            this.cameFrom=cameFrom;//added
            this.cost=cost;//added
        }
        
        //equals method added
        public boolean equals(Object other) {
             if(other==null) {
                  return false;
             }
             if(other instanceof MapLocation) {
                  if(this.x==((MapLocation) other).x &&
                     this.y==((MapLocation) other).y) {
                       return true;
                  }
             }
             return false;
        }
        
        //hashcode method added
        public int hashCode() {
             return x*1024+y;
        }
        //added for debugging
        public String toString() {
             return "\n("+x+","+y+")";
        }
    }

    Stack<MapLocation> path;
    int footmanID, townhallID, enemyFootmanID;
    MapLocation nextLoc;

    private long totalPlanTime = 0; // nsecs
    private long totalExecutionTime = 0; //nsecs

    public AstarAgent(int playernum)
    {
        super(playernum);

        System.out.println("Constructed AstarAgent");
    }

    @Override
    public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
        // get the footman location
        List<Integer> unitIDs = newstate.getUnitIds(playernum);

        if(unitIDs.size() == 0)
        {
            System.err.println("No units found!");
            return null;
        }

        footmanID = unitIDs.get(0);

        // double check that this is a footman
        if(!newstate.getUnit(footmanID).getTemplateView().getName().equals("Footman"))
        {
            System.err.println("Footman unit not found");
            return null;
        }

        // find the enemy playernum
        Integer[] playerNums = newstate.getPlayerNumbers();
        int enemyPlayerNum = -1;
        for(Integer playerNum : playerNums)
        {
            if(playerNum != playernum) {
                enemyPlayerNum = playerNum;
                break;
            }
        }

        if(enemyPlayerNum == -1)
        {
            System.err.println("Failed to get enemy playernumber");
            return null;
        }

        // find the townhall ID
        List<Integer> enemyUnitIDs = newstate.getUnitIds(enemyPlayerNum);

        if(enemyUnitIDs.size() == 0)
        {
            System.err.println("Failed to find enemy units");
            return null;
        }

        townhallID = -1;
        enemyFootmanID = -1;
        for(Integer unitID : enemyUnitIDs)
        {
            Unit.UnitView tempUnit = newstate.getUnit(unitID);
            String unitType = tempUnit.getTemplateView().getName().toLowerCase();
            if(unitType.equals("townhall"))
            {
                townhallID = unitID;
            }
            else if(unitType.equals("footman"))
            {
                enemyFootmanID = unitID;
            }
            else
            {
                System.err.println("Unknown unit type");
            }
        }

        if(townhallID == -1) {
            System.err.println("Error: Couldn't find townhall");
            return null;
        }

        long startTime = System.nanoTime();
        path = findPath(newstate);
        totalPlanTime += System.nanoTime() - startTime;

        return middleStep(newstate, statehistory);
    }

    @Override
    public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
        long startTime = System.nanoTime();
        long planTime = 0;

        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        if(shouldReplanPath(newstate, statehistory, path)) {
            long planStartTime = System.nanoTime();
            path = findPath(newstate);
            planTime = System.nanoTime() - planStartTime;
            totalPlanTime += planTime;
        }

        Unit.UnitView footmanUnit = newstate.getUnit(footmanID);

        int footmanX = footmanUnit.getXPosition();
        int footmanY = footmanUnit.getYPosition();

        if(!path.empty() && (nextLoc == null || (footmanX == nextLoc.x && footmanY == nextLoc.y))) {

            // stat moving to the next step in the path
            nextLoc = path.pop();

            System.out.println("Moving to (" + nextLoc.x + ", " + nextLoc.y + ")");
        }

        if(nextLoc != null && (footmanX != nextLoc.x || footmanY != nextLoc.y))
        {
            int xDiff = nextLoc.x - footmanX;
            int yDiff = nextLoc.y - footmanY;

            // figure out the direction the footman needs to move in
            Direction nextDirection = getNextDirection(xDiff, yDiff);

            actions.put(footmanID, Action.createPrimitiveMove(footmanID, nextDirection));
        } else {
            Unit.UnitView townhallUnit = newstate.getUnit(townhallID);

            // if townhall was destroyed on the last turn
            if(townhallUnit == null) {
                terminalStep(newstate, statehistory);
                return actions;
            }

            if(Math.abs(footmanX - townhallUnit.getXPosition()) > 1 ||
                    Math.abs(footmanY - townhallUnit.getYPosition()) > 1)
            {
                System.err.println("Invalid plan. Cannot attack townhall");
                totalExecutionTime += System.nanoTime() - startTime - planTime;
                return actions;
            }
            else {
                System.out.println("Attacking TownHall");
                // if no more movements in the planned path then attack
                actions.put(footmanID, Action.createPrimitiveAttack(footmanID, townhallID));
            }
        }

        totalExecutionTime += System.nanoTime() - startTime - planTime;
        return actions;
    }

    @Override
    public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {
        System.out.println("Total turns: " + newstate.getTurnNumber());
        System.out.println("Total planning time: " + totalPlanTime/1e9);
        System.out.println("Total execution time: " + totalExecutionTime/1e9);
        System.out.println("Total time: " + (totalExecutionTime + totalPlanTime)/1e9);
    }

    @Override
    public void savePlayerData(OutputStream os) {

    }

    @Override
    public void loadPlayerData(InputStream is) {

    }

    /**
     * You will implement this method.
     *
     * This method should return true when the path needs to be replanned
     * and false otherwise. This will be necessary on the dynamic map where the
     * footman will move to block your unit.
     *
     * @param state
     * @param history
     * @param currentPath
     * @return
     */
    private boolean shouldReplanPath(State.StateView state, History.HistoryView history, Stack<MapLocation> currentPath)
    {
        /*if(currentPath==null) {
             return true;
        } else if(currentPath.isEmpty()) {
             return false;
        }
        MapLocation peek=currentPath.peek();
        int enemyX=state.getUnit(enemyFootmanID).getXPosition()-peek.x;
        int enemyY=state.getUnit(enemyFootmanID).getYPosition()-peek.y;
        if(enemyX==0 && enemyY==1) {
             return true;
        } else {
             return false;
        }*/
         return false;
    }

    /**
     * This method is implemented for you. You should look at it to see examples of
     * how to find units and resources in Sepia.
     *
     * @param state
     * @return
     */
    private Stack<MapLocation> findPath(State.StateView state)
    {
        Unit.UnitView townhallUnit = state.getUnit(townhallID);
        Unit.UnitView footmanUnit = state.getUnit(footmanID);

        MapLocation startLoc = new MapLocation(footmanUnit.getXPosition(), footmanUnit.getYPosition(), null, 0);

        MapLocation goalLoc = new MapLocation(townhallUnit.getXPosition(), townhallUnit.getYPosition(), null, 0);

        MapLocation footmanLoc = null;
        if(enemyFootmanID != -1) {
            Unit.UnitView enemyFootmanUnit = state.getUnit(enemyFootmanID);
            footmanLoc = new MapLocation(enemyFootmanUnit.getXPosition(), enemyFootmanUnit.getYPosition(), null, 0);
        }

        // get resource locations
        List<Integer> resourceIDs = state.getAllResourceIds();
        Set<MapLocation> resourceLocations = new HashSet<MapLocation>();
        for(Integer resourceID : resourceIDs)
        {
            ResourceNode.ResourceView resource = state.getResourceNode(resourceID);

            resourceLocations.add(new MapLocation(resource.getXPosition(), resource.getYPosition(), null, 0));
        }

        return AstarSearch(startLoc, goalLoc, state.getXExtent(), state.getYExtent(), footmanLoc, resourceLocations);
    }
    
    //Begin Added Code
    
    /**
     * This is the method you will implement for the assignment. Your implementation
     * will use the A* algorithm to compute the optimum path from the start position to
     * a position adjacent to the goal position.
     *
     * You will return a Stack of positions with the top of the stack being the first space to move to
     * and the bottom of the stack being the last space to move to. If there is no path to the townhall
     * then return null from the method and the agent will print a message and do nothing.
     * The code to execute the plan is provided for you in the middleStep method.
     *
     * As an example consider the following simple map
     *
     * F - - - -
     * x x x - x
     * H - - - -
     *
     * F is the footman
     * H is the townhall
     * x's are occupied spaces
     *
     * xExtent would be 5 for this map with valid X coordinates in the range of [0, 4]
     * x=0 is the left most column and x=4 is the right most column
     *
     * yExtent would be 3 for this map with valid Y coordinates in the range of [0, 2]
     * y=0 is the top most row and y=2 is the bottom most row
     *
     * resourceLocations would be {(0,1), (1,1), (2,1), (4,1)}
     *
     * The path would be
     *
     * (1,0)
     * (2,0)
     * (3,1)
     * (2,2)
     * (1,2)
     *
     * Notice how the initial footman position and the townhall position are not included in the path stack
     *
     * @param start Starting position of the footman
     * @param goal MapLocation of the townhall
     * @param xExtent Width of the map
     * @param yExtent Height of the map
     * @param resourceLocations Set of positions occupied by resources
     * @return Stack of positions with top of stack being first move in plan
     */
    
    private Stack<MapLocation> AstarSearch(MapLocation start, MapLocation goal, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations)
    {
    	boolean goalFound = false;
    	PriorityQueue<MapLocation> openset = new PriorityQueue<MapLocation>(xExtent*yExtent - resourceLocations.size() - 1, new LocationComparator());
    	Hashtable<MapLocation, MapLocation> closedList = new Hashtable<MapLocation, MapLocation>();
    	openset.add(new MapLocation(start.x,start.y, start, 0));
    	MapLocation loc = null;
    	while(!openset.isEmpty() && !goalFound) {
    	     loc = openset.remove();
    		for(MapLocation child: getChildren(openset, loc, xExtent, yExtent, enemyFootmanLoc, resourceLocations, goal)) {
    			if (child.equals(goal)) {
    				goalFound = true;
    				break;
    			} else if(openset.contains(child)) {
    			     Iterator<MapLocation> iterator=openset.iterator();
    			     MapLocation temp=null;
    			     while(iterator.hasNext()) {
    			          temp=iterator.next();
    			          if(child.equals(temp)) {
         			          break;
    			          }
    			     }
         			if(temp.cost >= child.cost) {
                         iterator.remove();
                         openset.offer(child);
                    }
    			} else if(closedList.containsKey(child)) {
    			     continue;
    			} else {
    				openset.add(child);
    			}
    		}
    		closedList.put(loc, loc);
    		
    	}
    	if(!goalFound) {
    	     System.out.println("No available path");
    		return new Stack<MapLocation>();
    	}

        // return an empty path
        return calculateStack(loc);

    }
    
    
    private Stack<MapLocation> calculateStack(MapLocation end) {
    	Stack<MapLocation> endList = new Stack<MapLocation>();
    	MapLocation currentNode = end;
    	while(currentNode.cameFrom!=null) {
    		endList.push(currentNode);
    		currentNode = currentNode.cameFrom;
    	}
    	endList.pop();
    	return endList;
    }
    
    private LinkedList<MapLocation> getChildren(PriorityQueue<MapLocation> openList, MapLocation loc, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations, MapLocation goal) {
         LinkedList<MapLocation> locList= new LinkedList<MapLocation>();
    	if(isValid(loc.x-1, loc.y-1, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x-1, loc.y-1, loc, loc.cost + heuristic(loc.x-1, loc.y-1, goal)));
    	}
    	if(isValid(loc.x, loc.y-1, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x, loc.y-1, loc, loc.cost + heuristic(loc.x, loc.y-1, goal)));
    	}
    	if(isValid(loc.x+1, loc.y-1, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x+1, loc.y-1, loc, loc.cost + heuristic(loc.x+1, loc.y-1, goal)));
    	}
    	if(isValid(loc.x-1, loc.y, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x-1, loc.y, loc, loc.cost + heuristic(loc.x-1, loc.y, goal)));
    	}
    	if(isValid(loc.x+1, loc.y, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x+1, loc.y, loc, loc.cost + heuristic(loc.x+1, loc.y, goal)));
    	}
    	if(isValid(loc.x-1, loc.y+1, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x-1, loc.y+1, loc, loc.cost + heuristic(loc.x-1, loc.y+1, goal)));
    	}
    	if(isValid(loc.x, loc.y+1, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x, loc.y+1, loc, loc.cost + heuristic(loc.x, loc.y+1, goal)));
    	}
    	if(isValid(loc.x+1, loc.y+1, xExtent, yExtent, enemyFootmanLoc, resourceLocations)) {
    		locList.add(new MapLocation(loc.x+1, loc.y+1, loc, loc.cost + heuristic(loc.x+1, loc.y+1, goal)));
    	}
    	
    	return locList;
    }
    
    private boolean isValid(int x, int y, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations) {
        MapLocation temp = (new MapLocation(x, y, null, 0)); 
    	if(x>=0 && x<xExtent &&
            y>=0 && y<yExtent &&
            (enemyFootmanLoc==null || !temp.equals(enemyFootmanLoc)) &&
            (resourceLocations==null || !resourceLocations.contains(temp))) {
              return true;
         }
         else {
              return false;
         }
    }
    
    private int heuristic(int x, int y, MapLocation goal) {
         return Math.max(Math.abs(goal.x - x), Math.abs(goal.y - y));
    }
    
    private class LocationComparator implements Comparator<MapLocation> {

		@Override
		public int compare(MapLocation l1, MapLocation l2) {
			if(l1.cost >= l2.cost) {
				return 1;
			}
			return -1;
		}
    	
    }
    
    
    //End Added Code

    /**
     * Primitive actions take a direction (e.g. NORTH, NORTHEAST, etc)
     * This converts the difference between the current position and the
     * desired position to a direction.
     *
     * @param xDiff Integer equal to 1, 0 or -1
     * @param yDiff Integer equal to 1, 0 or -1
     * @return A Direction instance (e.g. SOUTHWEST) or null in the case of error
     */
    private Direction getNextDirection(int xDiff, int yDiff) {

        // figure out the direction the footman needs to move in
        if(xDiff == 1 && yDiff == 1)
        {
            return Direction.SOUTHEAST;
        }
        else if(xDiff == 1 && yDiff == 0)
        {
            return Direction.EAST;
        }
        else if(xDiff == 1 && yDiff == -1)
        {
            return Direction.NORTHEAST;
        }
        else if(xDiff == 0 && yDiff == 1)
        {
            return Direction.SOUTH;
        }
        else if(xDiff == 0 && yDiff == -1)
        {
            return Direction.NORTH;
        }
        else if(xDiff == -1 && yDiff == 1)
        {
            return Direction.SOUTHWEST;
        }
        else if(xDiff == -1 && yDiff == 0)
        {
            return Direction.WEST;
        }
        else if(xDiff == -1 && yDiff == -1)
        {
            return Direction.NORTHWEST;
        }

        System.err.println("Invalid path. Could not determine direction");
        return null;
    }
}
