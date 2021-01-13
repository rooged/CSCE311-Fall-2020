/* written by Timothy Gedney
 * CSCE311 Project 2
 */
package osp.Resources;

import java.util.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.Threads.*;
import osp.Utilities.*;
import osp.Memory.*;

/**
    Class ResourceCB is the core of the resource management module.
    Students implement all the do_* methods.
    @OSPProject Resources
*/
public class ResourceCB extends IflResourceCB
{
	public static Hashtable<ThreadCB, RRB> table;

    /**
       Creates a new ResourceCB instance with the given number of 
       available instances. This constructor must have super(qty) 
       as its first statement.

       @OSPProject Resources
    */
	
	
    public ResourceCB(int qty)
    {
       super(qty);
    }

    /**
       This method is called once, at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Resources
    */
	
    public static void init()
    {
       table = new Hashtable<ThreadCB, RRB>();
    }

    /**
       Tries to acquire the given quantity of this resource.
       Uses deadlock avoidance or detection depending on the
       strategy in use, as determined by ResourceCB.getDeadlockMethod().

       @param quantity
       @return The RRB corresponding to the request.
       If the request is invalid (quantity+allocated>total) then return null.

       @OSPProject Resources
    */
   	public RRB do_acquire(int quantity) {
		/*checks that quantity requested isn't 0, also checks that the requested quantity +
		  available doesn't go over the total possible of the resource*/
		if (quantity <= 0 || this.getTotal() < this.getAvailable() + quantity) {
			return null;
		}
		
		//gets thread currently running
		ThreadCB thread = MMU.getPTBR().getTask().getCurrentThread();
		
		//creates new rrb
		RRB rrb = new RRB(thread, this, quantity);
		
		//checks if quantity + allocated to thread isn't over the max the thread can claim
		if (this.getAllocated(thread) + quantity > this.getMaxClaim(thread)) {
			rrb.setStatus(Denied); //set satus to denied
			return null;
		}
		
		//runs bankers algorithm to check if the state is safe
		if (bankers(rrb, thread, quantity)) {
			rrb.setStatus(Granted);  //sets status to granted
			rrb.grant();  //grants rrb if state is safe
		} else {
			//if unsafe, suspends the thread and rrb
			if (thread.getStatus() != ThreadWaiting) {
				thread.suspend(rrb);
			}
			rrb.setStatus(Suspended);
		}
		
		//adds thread & rrb combo to hashtable
		table.put(thread, rrb);
		
		return rrb;
	}
	
	/**
		Banker's Algorithm method. Takes in an rrb, thread, and quantity. Returns a boolean
		value on whether or not the system will be deadlocked after running an rrb.
		
		@param rrb, thread, quantity
		@return true/false corresponding to deadlock
		
	*/
	public boolean bankers(RRB rrb, ThreadCB thread, int quantity) {
		/*checks that the quantity running isn't over the available resources, if so,
		  it returns false due to unsafe state*/
		if (quantity > this.getAvailable()) {
			//checks if thread is already suspended
			if (thread.getStatus() != ThreadWaiting) {
				thread.suspend(rrb);
			}
			//suspends with null empty rrb
			rrb = new RRB(thread, this, 0);
			rrb.setStatus(Suspended);
			return false;
		}
		
		/**
			Code used from provided deadlock prevention method, marking places where edited
		*/
		//1 - find the current available for all resources
		int[] available = new int[ResourceTable.getSize()];

		//loop through ResourceTable (getSize(), .getResourceCB(i).getAvailable()) 
		// to set available[i]
		for (int i = 0; i < ResourceTable.getSize(); i++) {
			available[i] = ResourceTable.getResourceCB(i).getAvailable();
		}
	
		//Need to create a list of Threads that have some resource allocated to it. 
		Hashtable<ThreadCB, Boolean> threadsWithRes = new Hashtable<>();
		
		//Easier to walk through an enumeration when deailing with HashTables... 
		// Enumeration<ThreadCB> and table.keys
		Enumeration<ThreadCB> enumKeys = table.keys();
		//Loop through the enumeration (.hasMoreElements() in a while)
		while (enumKeys.hasMoreElements()) {
			//grab thread enumeration (ThreadCB thread = enum.nextElement()) 
			ThreadCB enumThread = enumKeys.nextElement();
			//put all into the hash with Boolean of false
			threadsWithRes.put(enumThread, new Boolean(false)); 
			//Loop through resource Table and check to see if 
			for (int i = 0; i < ResourceTable.getSize(); i++) {
				if (ResourceTable.getResourceCB(i).getAllocated(thread) != 0 ) {
					threadsWithRes.put(enumThread, new Boolean(true));
					return false;
				}
			}
		}
		
		//while loop to continue until a safe running state is found
		boolean isDeadlock = true;
		while (true) {
			isDeadlock = true;
			//Set up the enumeration again to loop through the table keys
			enumKeys = table.keys();
			//While hasMoreElements
			while(enumKeys.hasMoreElements())
			{
			    //thread = enum.nextElement()
			    ThreadCB enumThread = enumKeys.nextElement();
			    //check if thread in the local hash is true (use the .get for hash - returns Boolean value)
			    if(threadsWithRes.get(enumThread))
			    {
					//extract the quantity that the quantity of the resource that the 
					//thread requested on the RRB (table.get(thread).getQuantity()) 
					int quant = table.get(enumThread).getQuantity();
					//DO THIS FIRST! if this quantity is not 0 
					
					
					if(quant != 0)
					{
						/**
							checks if quantity doesn't go over available.
							checks that the quantity+allocated doesn't go over the threads max claim
						*/
						int availRes = available[table.get(enumThread).getResource().getID()];
						if(quant <= availRes && table.get(enumThread).getResource().getMaxClaim(enumThread) >=
						quant + table.get(enumThread).getResource().getAllocated(enumThread)) {
							//for (int i = 0; i < ResourceTable.getSize(); i++) {
							//if (ResourceTable.getResourceCB(i).getMaxClaim(enumThread) > quant + ResourceTable.getResource(i).getAllocated()) {
							//then we need to figure out if the quantity is lt the available for that resource
							//available[table.get(thread).getResource().getID()]
							//if less than then we can hypothetically field this request
							//Loop through the resourcetable and set available[i] +=
							//what the thread was allocated RT.getResourceCB(i).getAllocated(thread)
							for(int i = 0; i < ResourceTable.getSize(); i++)
							{
								available[i] += ResourceTable.getResourceCB(i).getAllocated(enumThread);
							}
							threadsWithRes.put(enumThread, new Boolean(false));
							isDeadlock = false;
							//put it back in the local hash as Boolean of false
							//set isDeadlock to false
						}
					}
				}
			}
			//breaks from whlie loop once deadlock is finally avoided
			if (isDeadlock) {
				break;
			}
		}
		//returns safe state
		return true;
		/**
			End of provided deadlock code
		*/
	}
  
    /**
       When a thread was killed, this is called to release all
       the resources owned by that thread.

       @param thread -- the thread in question

       @OSPProject Resources
    */
    public static void do_giveupResources(ThreadCB thread)
    {
		//loops through resources to remove allocated to thread and set available
		for (int i = 0; i < ResourceTable.getSize(); i++) {
			ResourceCB resource = ResourceTable.getResourceCB(i);
			resource.setAvailable(resource.getAvailable() + resource.getAllocated(thread));
			resource.setAllocated(thread, 0);
		}
		//remove thread from hashtable
		table.remove(thread);
		
		//loops through hashtable to find rrb to run
	   	Enumeration<ThreadCB> keys = table.keys();
		while (keys.hasMoreElements()) {
			//pulls thread from hashtable
			ThreadCB key = keys.nextElement();
			//gets rrb from thread
			RRB rrb = table.get(key);
			/*checks that quantity is less than available, checks that key isn't same as the
			  same thread, and runs the banker's algorithm to ensure there won't be deadlock*/
			if (rrb.getQuantity() <= rrb.getResource().getAvailable() && key != thread &&
			rrb.getResource().bankers(rrb, key, rrb.getQuantity())) {
				rrb.setStatus(Granted); //set status to granted
				//checks that thread isn't in kill state
				if (key.getStatus() != ThreadKill) {
					rrb.grant();  //grant rrb
					RRB empty = new RRB(null, null, 0);  //update hash table with empty rrb
					table.put(key, empty);  //add empty rrb
				}
			}
    	}
    }

    /**
        Release a previously acquired resource.

	@param quantity

        @OSPProject Resources
    */
    public void do_release(int quantity)
    {
		//gets current thread
		ThreadCB thread = MMU.getPTBR().getTask().getCurrentThread();
		//removes allocated amount from resource & sets available
		this.setAllocated(thread, this.getAllocated(thread) - quantity);
		this.setAvailable(this.getAvailable() + quantity);
		
		//loops through hashtable to find rrb to run
		Enumeration<ThreadCB> keys = table.keys();
		while (keys.hasMoreElements()) {
			//pulls thread from hashtable
			ThreadCB key = keys.nextElement();
			//gets rrb from thread
			RRB rrb = table.get(key);
			/*checks that quantity is less than available, checks that key isn't same as the
			  same thread, and runs the banker's algorithm to ensure there won't be deadlock*/
			if (rrb.getQuantity() <= rrb.getResource().getAvailable() && key != thread &&
			rrb.getResource().bankers(rrb, key, rrb.getQuantity())) {
				rrb.setStatus(Granted);  //set status to granted
				//checks that thread isn't in kill state
				if (key.getStatus() != ThreadKill) {
					rrb.grant();  //grant rrb
					RRB empty = new RRB(null, null, 0);  //update hash table with empty rrb
					table.put(key, empty); //add empty rrb
				}
			}
		}
    }

    /**
       Performs deadlock detection.
       @return A vector of ThreadCB objects found to be in a deadlock.

       @OSPProject Resources
    */
    public static Vector do_deadlockDetection()
    {
       //1 - find the current available for all resources
       int[] available = new int[ResourceTable.getSize()];
       
       //loop through ResourceTable (getSize(), .getResourceCB(i).getAvailable()) 
	   // to set available[i]
       for(int i = 0; i < ResourceTable.getSize(); i++) 
       {
		   available[i] = ResourceTable.getResourceCB(i).getAvailable();
	   }
	   
		//Need to create a list of Threads that have some resource allocated to it. 
		Hashtable<ThreadCB, Boolean> threadsWithRes = new Hashtable<>();
		
		//Easier to walk through an enumeration when deailing with HashTables... 
		// Enumeration<ThreadCB> and table.keys
		Enumeration<ThreadCB> enumKeys = table.keys();
		//Loop through the enumeration (.hasMoreElements() in a while)
		while(enumKeys.hasMoreElements())
		{
			//grab thread enumeration (ThreadCB thread = enum.nextElement()) 
			ThreadCB thread = enumKeys.nextElement();
			//put all into the hash with Boolean of false
			threadsWithRes.put(thread, new Boolean(false)); 
			//Loop through resource Table and check to see if 
			for(int i = 0; i < ResourceTable.getSize(); i++)
			{
				if(ResourceTable.getResourceCB(i).getAllocated(thread) != 0 )
				{
					threadsWithRes.put(thread, new Boolean(true));
					break;
				}
			}
			//ResourceTable.getResource(i).getAllocated(thread) > 0) 
			   //if yes then put the thread into the hash again with a value of true (and break)
		}
		//On to deadlock detection 
		//At the beginning we are assuming that all of the threads marked as true are in deadlock
		//We need to start eliminating. 
		 
		boolean isDeadlock = true;
		while (true) {
			isDeadlock = true;
			//Set up the enumeration again to loop through the table keys
			enumKeys = table.keys();
			//While hasMoreElements
			while(enumKeys.hasMoreElements())
			{
			    //thread = enum.nextElement()
			    ThreadCB thread = enumKeys.nextElement();
			    //check if thread in the local hash is true (use the .get for hash - returns Boolean value)
			    if(threadsWithRes.get(thread))
			    {
					//extract the quantity that the quantity of the resource that the 
					//thread requested on the RRB (table.get(thread).getQuantity()) 
					int quantity = table.get(thread).getQuantity();
					//DO THIS FIRST! if this quantity is not 0 
					
					
					if(quantity != 0)
					{
						int availRes = available[table.get(thread).getResource().getID()];
						if(quantity <= availRes){
							//then we need to figure out if the quantity is lt the available for that resource
							//available[table.get(thread).getResource().getID()]
							//if less than then we can hypothetically field this request
							//Loop through the resourcetable and set available[i] +=
							//what the thread was allocated RT.getResourceCB(i).getAllocated(thread)
							for(int i = 0; i < ResourceTable.getSize(); i++)
							{
								available[i] += ResourceTable.getResourceCB(i).getAllocated(thread);
							}
							threadsWithRes.put(thread, new Boolean(false));
							isDeadlock = false;
							//put it back in the local hash as Boolean of false
							//set isDeadlock to false
						}
					}
				}
			}
			if (isDeadlock) {
				break;
			}
		}

		//Now to create the output (Vector of threads involved in Deadlock
		Vector<ThreadCB> results = new Vector<>();
		
		Enumeration<ThreadCB> localKeys = threadsWithRes.keys();
		while(localKeys.hasMoreElements())
		{
			ThreadCB thread = localKeys.nextElement();
			if(threadsWithRes.get(thread)){
				results.addElement(thread);
			}
		}
		if(results.isEmpty()) return null;
		//loop through the local hash keys
			//If value for that thread is true then addElement(thread) to results. 
		//If results.isEmpty() return null 
		
		//Now the clean up. 
		//Loop through results (.size()) and kill all the threads found to be in deadlock 
		//results.get(i), thread.kill()
		for(int i = 0; i < results.size(); i++)
		{
			results.get(i).kill();
		}
		
		//There may be threads that can run now. 
		Enumeration<ThreadCB> enum3 = table.keys();
		RRB nullRRB = null;
		while(enum3.hasMoreElements())
		{
		//loop through table.keys()
			ThreadCB thread = enum3.nextElement();
			RRB rrb = table.get(thread);
			if(rrb != null)
			{
				try{
					
					if(rrb.getResource().getAvailable() >= rrb.getQuantity())
					{
						rrb.grant();
						table.put(thread, nullRRB);
					}
				}catch(Exception e){
				}
			}
			//if rrb at table.get(key) != null && 
			//rrb.getQuantity() <= rrb.getResource().getAvailable()
				//rrb.grant()
				//put thread back on table (rrb.getThread()) with null rrb
		}

		return results;
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Resources
    */
	
    public static void atError()
    {
        // your code goes here

    }
  
    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
     
	@OSPProject Resources
    */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
