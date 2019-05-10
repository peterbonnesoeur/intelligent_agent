package template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.topology.Topology.City;



public class Coordination {
	
		enum Status { PICKUP,DELIVER};
		enum Placement {PREVIOUS,LAST};

		public ArrayList<Vehicle> vehicles = new ArrayList<Vehicle>();
		private Random rand;
		private HashMap<Group,Vehicle> group2Vehicle = new HashMap<Group,Vehicle>();
		private HashMap<Vehicle,Sequence> sequences = new HashMap<Vehicle,Sequence>();
				
		/* Each vehicle have an associated Sequence */
		
		/********************* Constructors of Coordination***************************/
		public Coordination(Coordination coord) 
		{
			this.vehicles.addAll(coord.vehicles);
			for (Vehicle v :coord.vehicles) {
				 Sequence mySequence = new Sequence (coord.getSequences().get(v));
				 mySequence.updateGroup2Vehicle();
				sequences.put(v,mySequence);
			}
			this.rand = coord.rand;
		}
		

		
		public Coordination(List<Vehicle> myVehicles, ArrayList<Task> myTasks, long seed) {
			
			/* Fill the Sequences with the tasks and assign them to the vehicle*/
			
			this.vehicles.addAll(myVehicles);
			this.rand = new Random(seed);
			ArrayList<Task> TaskLeft = new ArrayList<Task>(myTasks);
			
			
			ArrayList<ArrayList<Task>> ArrayTasks = new ArrayList<ArrayList<Task>>();
			
			for(int i = 0; i<myVehicles.size();i++)
				ArrayTasks.add(new ArrayList<Task>());
			
			
			int selectedV = 0;
			for(Task task:TaskLeft) 
			{
				selectedV = rand.nextInt(myVehicles.size());
				ArrayTasks.get(selectedV).add(task);
			}
			
			int iterS=0;
			for(Vehicle V:myVehicles)
			{
				sequences.put(V, new Sequence(ArrayTasks.get(iterS),V));
				
				iterS++;
			}
		}
		
		/********************* Mutation functions of Coordination***************************/

		
		public Coordination mutate(double gamma) {
			
			/*Function that centralize the different possible mutation of our Simulation*/
			
			
			Coordination mut1 = new Coordination(this);
			double localgamma = gamma;
			
			if(rand.nextDouble()>0.5*localgamma) {
				mut1.changeSeqVehicle();
			}
			for(int i=0; i< vehicles.size();i++)
			{
				double randomVal=rand.nextDouble();
				if(randomVal<localgamma*0.2)
    			{
    				mut1.changeGroupSequence();
    			}else
				if(randomVal<localgamma*0.4)
    			{
					mut1.sequences.get(vehicles.get(i)).changeGroupOrder();
    			}else
				if(randomVal<localgamma*0.6)
    			{
					mut1.sequences.get(vehicles.get(i)).changeTaskGroup();
    			}else
				if(randomVal<localgamma*0.8)
    			{
					//System.out.println("we mutate 4 ");
					mut1.sequences.get(vehicles.get(i)).sequenceChangeTaskOrder();
				}
			}
			return mut1;
		}

		
		private void changeSeqVehicle(){
			
			/*	Permute the sequence of 2 vehicles */
			
			if(vehicles.size()>1)
			{
				Vehicle V1 = vehicles.get(rand.nextInt(vehicles.size()));
				Vehicle V2 = null;
				do {
					V2 = vehicles.get(rand.nextInt(vehicles.size()));
				}while(V2==V1);
				Sequence tempSeq = new Sequence(sequences.get(V1));
				if(V2.capacity()>=tempSeq.getMaxWeight())
				{
					tempSeq.setVehicle(V2);
					sequences.put(V1,sequences.get(V2));
					sequences.get(V1).setVehicle(V1);
					sequences.put(V2,tempSeq);
					updateGroup2Vehicle();
				}
			}
		}
		
		private void changeGroupSequence() {
			
			/*	Put a group of tasks from one sequence to an other other one*/
			/*  This group will then change of vehicle*/
			if(vehicles.size()>1)
			{
				Vehicle V1, V2 = null;
				
				do 
				{
					V1 = vehicles.get(rand.nextInt(vehicles.size()));
				}
				while(sequences.get(V1).getGroups().size()==0);
				
				do 
				{
					V2 = vehicles.get(rand.nextInt(vehicles.size()));
				}
				while(V1==V2);
				
				sequences.get(V2).addGroup(sequences.get(V1).getGroups().remove(rand.nextInt(sequences.get(V1).getGroups().size())));
				
				sequences.get(V1).updateSeqCost();
				sequences.get(V2).updateSeqCost();
			}
		}
		
		public void addTaskToSequence(Task myTask,int index) {
			
			int  i = 0;
			for (Vehicle v : vehicles) {
				if (i == index) {
					sequences.get(v).placeTask(myTask);
				}
				i++;
			}
			
			
		}
		
		/********************* Get functions of Coordination***************************/

		
		public HashMap<Vehicle,Sequence> getSequences()
		{
			return sequences;
		}
		public List<Plan> getPlans(){
			//System.out.println( " in getPlan");
			
			updateGroup2Vehicle();
			List<Plan> plans = new ArrayList<Plan>();
			for(Vehicle V : vehicles) {
				plans.add(sequences.get(V).planGenerator());
			}
			return plans;
		}
		public double getTotalCost() {
			double totalCost=0;
			for(Vehicle V:vehicles)
			{
				totalCost+=sequences.get(V).updateSeqCost();
			}
			return totalCost;
		}
		
		public ArrayList<City> getLastCity(){
			
			ArrayList<City> finalCities = new ArrayList<City>();
			for (Vehicle V:vehicles)
				finalCities.add(sequences.get(V).getLastCitySeq());
			
			return finalCities;
		} 
		
		public ArrayList<City> getPreviousCity(){
			
			ArrayList<City> finalCities = new ArrayList<City>();
			for (Vehicle V:vehicles)
				finalCities.add(sequences.get(V).getPreviousCitySeq());
			
			return finalCities;
		} 
		
		/********************* Update functions of Coordination***************************/
		
		public void updateGroup2Vehicle() {
			
			/*Update the table which associate to each group the vehicle that are carrying them*/
			
			group2Vehicle=new HashMap<Group,Vehicle>();
			for(Vehicle V:vehicles)
			{
				sequences.get(V).updateGroup2Vehicle();
			}
		}



		private class Sequence{
		
	    	private ArrayList<Group> groups = new ArrayList<Group>();
	    	private Vehicle seqVehicle=null;
	    	private double seqCost = 0;
	    	
			/********************* Constructors of Sequence ***************************/
	
	    	
	    	public Sequence(ArrayList<Task> seqTasks,Vehicle currentVehicle) {
	
	    		this.seqVehicle = currentVehicle;
	    		
	    		if(seqTasks!=null)
	    		{
	    			for(int i=0;i<seqTasks.size();i++)
	    			{
	    				Group currentGroup = new Group(new ArrayList<Task>(), new ArrayList<Event>());				
	    				this.groups.add(currentGroup);
	    				
	    				group2Vehicle.put(currentGroup, currentVehicle);
	    			}
	    			for(Task task:seqTasks) 
	    			{
	    				int i=0;
	    				do {
	    				i = rand.nextInt((this.groups.size()));
	    				}while(!this.groups.get(i).addTask(task));	
	    			}
	    		}
	    	}
	    	
	



			public Sequence(Sequence sequence) {
	    		this.seqVehicle = sequence.getSeqVehicle();
	    		for (int i = 0;i<sequence.groups.size();i++) {
	    			this.groups.add(new Group(sequence.groups.get(i)));
	    		}
	    		this.seqCost = sequence.getSeqCost();
	    	}
	    	
	    	/********************* Mutation functions of Sequence ***************************/
	
	    	
	    	public void changeTaskGroup() {
	    		
	    		/* Pick a task from a group in a sequence and put it in an other group while staying in the same sequence */
	    		
	    		int taskNumber = 0;
	    		for (int i = 0 ;i<groups.size();i++) 
	    			taskNumber += groups.get(i).getGroupTasks().size();
	    		
	    		
	    		if(groups.size()>1 && taskNumber>1)
	    		{
	    			int index =0;
	    			for (int i = 0; i<groups.size();i++)
	    			
	    			do index =rand.nextInt(groups.size());
	    			while(groups.get(index).getGroupTasks().size()==0);
	    			
	    			Task myTask = groups.get(index).removeTask(rand.nextInt(groups.get(index).getGroupTasks().size()));
	    				    			
	    			int newIndex = index;
	    			do {
	    			 newIndex = rand.nextInt(groups.size());
	    			}while(newIndex == index);
	    			
	    			updateGroup2Vehicle();
	    			while(!groups.get(newIndex).addTask(myTask)) {}
	    		}		
	    	}
	    	
	    	public void sequenceChangeTaskOrder() {
	    		
	    		/* Change the order of the tasks in a group */
	    		
	    		int myIndex = 0;
	    		ArrayList<Integer> indexes = new ArrayList<Integer>();
	    		
	    		for (int i = 0 ;i<groups.size();i++) {
	    			if(groups.get(i).getGroupTasks().size()>=2) {
	    				indexes.add(i);
	    			}
	    		}
	    		if(indexes.size()!=0) {
	    			myIndex = indexes.get(rand.nextInt(indexes.size()));
	    			groups.get(myIndex).changeTaskOder();
	    		}	
			}
		    	
			public void changeGroupOrder() {
	    		if(groups.size()>0)
	    			groups.add(groups.remove(rand.nextInt(groups.size())));
				
			}
			
			public void addGroup(Group newGroup) {
	    		if(groups.size()>0)
	    			groups.add(rand.nextInt(groups.size()),newGroup);
	    		else
	    			groups.add(newGroup);	
			}
	    	
			
			
			/* If we can create a new class that extends this one it would be cool i think*/
			
			public void placeTask(Task myTask) {
				
				City previousCity = getPreviousCitySeq();
				
				
					
				int index = 0;
				for (int i = 0 ; i<groups.size();i++) {
					if (groups.get(i).groupTasks.size() != 0)
						index = i;	
				}
					
				if(myTask.pickupCity == previousCity) {
					groups.get(index).putTaskEnd(Placement.PREVIOUS,myTask);
				}else
					groups.get(index).putTaskEnd(Placement.LAST,myTask);

					
					
				
				
			}
		
		/********************* Update function of Sequence ***************************/
	
	
		
	    	public void updateGroup2Vehicle() {
	    		
	    		/* update the vehicle linked to each group*/
	    	
	    		for(int i=0;i<groups.size();i++) {
	    			group2Vehicle.put(groups.get(i), seqVehicle);
	    		}
	    	}
	    	
	   /********************* Get functions of Sequence***************************/
	
	    	public ArrayList<Group> getGroups() {
	    		return groups;
	    	}
	    	
	    	public Vehicle getSeqVehicle(){
	    		return seqVehicle;
	    	}
	    	
	    	public double getSeqCost() {
	    		return seqCost;
	    	}
	    	
	    	public void setVehicle(Vehicle V) {
	    		seqVehicle=V;
	    	}
	    	
	    	
	    	public City getLastCitySeq() {
				ArrayList<Event> seqEvents = new ArrayList<Event>(getSeqEvents());
				if (seqEvents.size() == 0)
					return null;
				
				return seqEvents.get(seqEvents.size()-1).getTask().deliveryCity;
			}
	    	
	    	public City getPreviousCitySeq() {
				ArrayList<Event> seqEvents = new ArrayList<Event>(getSeqEvents());
				if (seqEvents.size() == 0)
					return null;
				
				City beginCity = null;
				
				if (seqEvents.size() <= 2) 
					beginCity=  seqVehicle.homeCity();
				else	{
					beginCity = seqEvents.get(seqEvents.size()-1).getTask().deliveryCity;
				
					if(seqEvents.get(seqEvents.size()-2).getTask().deliveryCity==beginCity)
						beginCity = seqEvents.get(seqEvents.size()-3).getTask().deliveryCity;
					else
						beginCity = seqEvents.get(seqEvents.size()-2).getTask().deliveryCity;
				}
				
				for (City c :  beginCity.pathTo(seqEvents.get(seqEvents.size()-1).getTask().deliveryCity)){
					if (c != seqEvents.get(seqEvents.size()-1).getTask().deliveryCity)
						beginCity = c;
				}
				
				return beginCity;
				
			}
	    	
		    private ArrayList<Event> getSeqEvents(){
		    		
		    		ArrayList<Event> seqEvents = new ArrayList<Event>();
		    		
		    		for(int i=0;i<groups.size();i++)
		    		{
		    			seqEvents.addAll(groups.get(i).getGroupEvents());
		    		}
		    		return seqEvents;
		    	}
		    
		    public int getMaxWeight()
		    {    
		    	/*Gives us the maximum weight carried by the current sequence during the simulation*/
		    		int maxWeight=0;
			    	
			    	for(int i=0;i<groups.size();i++)
			    	{
			    		if(maxWeight<groups.get(i).getMaxWeight())
			    		{
			    			maxWeight = groups.get(i).getMaxWeight();
			    		}
			    	}
			    	return maxWeight;
		    }
		    
		   
		/********************* Update functions of Sequence***************************/
	
		    public double updateSeqCost() {
		    	
		    	/*	Compute the cost of the current Sequence */
		    	
	    		City myCity=seqVehicle.getCurrentCity();
	    		Event myEvent=null;
	    		ArrayList<Event> seqEvents = new ArrayList<Event>(this.getSeqEvents()); //might not work
	    		seqCost = 0;
	    		for(int i=0;i<seqEvents.size();i++)
	    		{
	    			myEvent = seqEvents.get(i);
	    			if(myEvent.getStatus()==Status.PICKUP)
	    			{
	    				seqCost += myCity.distanceTo(myEvent.getTask().pickupCity);
	    				myCity = myEvent.getTask().pickupCity;
	    			}else 
	    			{
	    				seqCost += myCity.distanceTo(myEvent.getTask().deliveryCity);
	    				myCity = myEvent.getTask().deliveryCity;
	    			}
	    		}
	    		seqCost *= seqVehicle.costPerKm();
	    		return seqCost;
		    }
	
			/********************* Plan generator function of Sequence***************************/
	
		    
	    	public Plan planGenerator() {
	    		
	    		/*Generate the plan from a Sequence*/
	    		
	    		City myCity=seqVehicle.getCurrentCity();
	
	    		Plan seqPlan = new Plan(myCity);
	    		
	    		Event myEvent=null;
	    		ArrayList<Event> seqEvents = new ArrayList<Event>(this.getSeqEvents()); //might not work
	    		
	    		
	    		for(int i=0;i<seqEvents.size();i++)
	    		{
	    			myEvent = seqEvents.get(i);
	    			if(myEvent.getStatus()==Status.PICKUP)
	    			{
	    				for (City path : myCity.pathTo(myEvent.getTask().pickupCity))
	    					seqPlan.appendMove(path);
					
	    				seqPlan.appendPickup(myEvent.getTask());
	    				myCity = myEvent.getTask().pickupCity;
	    			}else 
	    			{
						for (City path : myCity.pathTo(myEvent.getTask().deliveryCity))
							seqPlan.appendMove(path);
		
	    				seqPlan.appendDelivery(myEvent.getTask());
	    				myCity = myEvent.getTask().deliveryCity;
	    			}
	    		}
	    		return seqPlan;
	    	}
		}


	private class Group{
		
		private ArrayList<Task> groupTasks = new ArrayList<Task>();
		private ArrayList<Event> groupEvents = new ArrayList<Event>();
		
		
		
		/********************* Constructors of Group ***************************/

		
		public Group (Group newGroup) {
			this.groupTasks.clear();
			this.groupTasks.addAll(newGroup.getGroupTasks());
			this.groupEvents.clear();
			for (int i = 0; i< newGroup.getGroupEvents().size();i++)
			this.groupEvents.add(i,new Event(newGroup.getGroupEvents().get(i)));
		}
		
		public Group(ArrayList<Task> myTasks, ArrayList<Event> myEvents) {
			this.groupTasks.addAll(myTasks);
			this.groupEvents.addAll(myEvents);
		}

		
		/********************* Mutation functions of Group ***************************/

		
		public void changeTaskOder() {
			
			/* Change the order of the tasks for the current Sequence*/
			ArrayList<Event> previousEvent = new ArrayList<Event> ();
			
			for (int i = 0; i< groupEvents.size();i++)
    			previousEvent.add(i,new Event(groupEvents.get(i)));
			
			while(previousEvent.equals(groupEvents)){
				Task myTask = removeTask(rand.nextInt(groupTasks.size()));
				addTask(myTask);
			}
		}
		
		public void putTaskEnd(Placement position, Task myTask) {
			
			switch (position){
				case PREVIOUS :
					int index  = groupEvents.size()-1;
					if (groupEvents.size()!=0 && ((groupEvents.get(index).getWeight() + myTask.weight)>group2Vehicle.get(this).capacity() ) ) {
						groupEvents.add(index,new Event (myTask,Status.PICKUP,myTask.weight+groupEvents.get(index).eventWeight ));
						updateWeight(index,myTask.weight);
						groupEvents.add(groupEvents.size(),new Event (myTask,Status.DELIVER,0));
					}else {
						
						index = groupEvents.size();
						groupEvents.add(index,new Event (myTask,Status.PICKUP,myTask.weight));
						groupEvents.add(groupEvents.size(),new Event (myTask,Status.DELIVER,0));
						
					}
					break;
					
				case LAST:
					index = groupEvents.size();
					groupEvents.add(index,new Event (myTask,Status.PICKUP,myTask.weight));
					groupEvents.add(groupEvents.size(),new Event (myTask,Status.DELIVER,0));
					break;
			}
			
		}
		
		public Task removeTask(int taskIndex) {
			/*Remove the task of index taskIndex of the current Group*/
			
			Task myTask = groupTasks.remove(taskIndex);
			
			ArrayList<Integer> toRemove = new ArrayList<Integer>();
			
			for(int i=0;i<groupEvents.size();i++)
			{
				if(groupEvents.get(i).getTask()==myTask)
				{
					toRemove.add(i);
					if (groupEvents.get(i).getStatus() == Status.DELIVER)
						updateWeight(i, -myTask.weight);
					
					else 
						updateWeight(i, myTask.weight);
				}
			}
			if (toRemove.size()!=0) {
				groupEvents.remove(toRemove.get(1).intValue());
				groupEvents.remove(toRemove.get(0).intValue());
			}
			return myTask;
		}
		
		public boolean addTask(Task task) {
			/* Add the Task task to the current Group */
			
			if(task.weight>group2Vehicle.get(this).capacity()) 
				return false;
			
			//if the vehicle is empty and can contain the task
			if(groupTasks.size()==0) {
				groupTasks.add(task);
				groupEvents.add(new Event(task,Status.PICKUP,task.weight));
				groupEvents.add(new Event(task, Status.DELIVER,0));
				return true;
			}
			//if the vehicle has many tasks
				
			ArrayList<Event> tempEvents = new ArrayList<Event>();
			tempEvents.addAll(groupEvents);
			
			int pickupOrder = rand.nextInt((tempEvents.size()));
			
			while((tempEvents.get(pickupOrder).getWeight()+task.weight)>group2Vehicle.get(this).capacity() && pickupOrder!=tempEvents.size())
				pickupOrder = rand.nextInt((tempEvents.size()));
			
			
			tempEvents.add(pickupOrder ,new Event(task,Status.PICKUP,tempEvents.get(pickupOrder).getWeight()+task.weight));
				
			int deliveryOrder = pickupOrder +1;
			
			this.updateWeight(pickupOrder+1,task.weight);
			
			int range = 0;
			while(tempEvents.get(deliveryOrder+range).getWeight()<group2Vehicle.get(this).capacity() 
					&& (deliveryOrder+range)<(tempEvents.size()-1))
				range++;
			
			
			if(range!=0)
				deliveryOrder = deliveryOrder+range;						

			
			tempEvents.add(deliveryOrder,new Event(task,Status.DELIVER,tempEvents.get(deliveryOrder).getWeight()));
			
			this.updateWeight(deliveryOrder, -task.weight);
			
			if((pickupOrder==0 && deliveryOrder == 1) || (pickupOrder==tempEvents.size()-2))
				return false;
			
			groupEvents.clear();
			groupEvents.addAll(tempEvents);
			
			return true;
		}
		
		/********************* Get functions of Group ***************************/

		public int getMaxWeight() {
			/*Gives us the maximum weight carried by a Group during the simulation*/
			
			int maxWeight=0;
			int currentWeight=0;
			
			for(int i=0;i<groupEvents.size();i++)
			{
				currentWeight = groupEvents.get(i).getWeight();
				if(currentWeight>maxWeight)
				{
					maxWeight = currentWeight;
				}
			}
			
			return maxWeight;
		}
		
		public ArrayList<Task> getGroupTasks(){
			return groupTasks;
		}
		
		public ArrayList<Event> getGroupEvents(){
			return groupEvents;
		}
		
		/********************* Get functions of Group ***************************/

		
		public void updateWeight(int eventIndex, int taskWeight)
		{
			for(int i=eventIndex+1;i<groupEvents.size();i++)
				groupEvents.get(i).ChangeWeight(groupEvents.get(i).eventWeight+taskWeight);
		}	
	}

//Event class stores the action that should be taken next with the current carried weight

	private class Event{
	
		private Task eventTask;
		private Status eventStatus;
		private int eventWeight;
		
		/********************* Constructors of Event ***************************/
		
		public Event(Task myTask, Status myStatus, int myWeight)
		{
			this.eventTask = myTask;
			this.eventStatus = myStatus;
			this.eventWeight = myWeight;
		}
		
		public Event (Event newEvent) {
			this.eventTask = newEvent.getTask();
			this.eventStatus = newEvent.getStatus();
			this.eventWeight = newEvent.getWeight();
		}
		
		/********************* Update function of Event ***************************/
		
		public void ChangeWeight(int newWeight)
		{
			/*Change the weight of the Event (Change the carried weight at this moment of the simulation)*/
			this.eventWeight=newWeight;
		}
		
		/********************* Get functions of Event ***************************/

		
		public int getWeight() {
			return eventWeight;
		}
		
		public Task getTask() {
			return eventTask;
		}
		
		public Status getStatus() {
			return eventStatus;
		}
	}
}

