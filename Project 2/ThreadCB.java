/* written by Timothy Gedney
 * CSCE311 Project 2
 */
package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
    public static GenericList readyList;    //global variable structure/list

    /**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */
    public ThreadCB()
    {
        super();    //call super per child constructor requirement
    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init()
    {
        readyList = new GenericList();  //initialize structure/list
    }

    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.

	The priority of the thread can be set using the getPriority/setPriority
	methods. However, OSP itself doesn't care what the actual value of
	the priority is. These methods are just provided in case priority
	scheduling is required.

	@return thread or null

        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task)
    {
        //checks that task isn't null, returns null if so
        if (task == null) {
            return null;
        }

        //if/else checks that task isn't already at max thread count, returns null if so
        if (task.getThreadCount() < MaxThreadsPerTask) {
            //creates new thread and associates it to the task
            ThreadCB newThread = new ThreadCB();
            newThread.setTask(task);
            //try/catch to make sure the task can be associated with the thread, returns null if not
            try {
                task.addThread(newThread);
            } catch (Exception e) {
                dispatch();
                return null;
            }
            //set threads status to ready
            newThread.setStatus(ThreadReady);

            //add thread to FCFS structure & returns the thread
            readyList.append(newThread);
            dispatch();
            return newThread;
        } else {
            dispatch();
            return null;
        }
    }

    /** 
	Kills the specified thread. 

	The status must be set to ThreadKill, the thread must be
	removed from the task's list of threads and its pending IORBs
	must be purged from all device queues.
        
	If some thread was on the ready queue, it must removed, if the 
	thread was running, the processor becomes idle, and dispatch() 
	must be called to resume a waiting thread.
	
	@OSPProject Threads
    */
    public void do_kill()
    {
        //checks if thread is ready, removes it from the FCFS list if so
        if (this.getStatus() == ThreadReady) {
            readyList.remove(this);
        //checks if thread is already running
        } else if (this.getStatus() == ThreadRunning) {
            /*preemptive context switching, checks that the thread is running,
            * sets base page register to null then sets the tasks current thread to null
            */
            if (this == MMU.getPTBR().getTask().getCurrentThread()) {
                MMU.setPTBR(null);
                this.getTask().setCurrentThread(null);
            }
        }

        //sets status to kill
        this.setStatus(ThreadKill);

        //loops through the device to purge all IORB associated with the thread
        for (int i = 0; i < Device.getTableSize(); i++) {
            Device.get(i).cancelPendingIO(this);
        }

        //release any resources associated with the thread
        ResourceCB.giveupResources(this);

        //removes the thread from the task
        this.getTask().removeThread(this);

        //checks if the tasks has any other threads, if not then it kills the task
        if (this.getTask().getThreadCount() == 0) {
            this.getTask().kill();
        }
        dispatch();
    }

    /** Suspends the thread that is currenly on the processor on the 
        specified event. 

        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
	
	Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.

	@param event - event on which to suspend this thread.

        @OSPProject Threads
    */
    public void do_suspend(Event event)
    {
        //checks the current status of thread, if it's running then it context switches
        if (this.getStatus() == ThreadRunning) {
            //sets status to waiting, sets register to null, and current thread to null
            if (this == MMU.getPTBR().getTask().getCurrentThread()) {
                this.setStatus(ThreadWaiting);
                MMU.setPTBR(null);
                this.getTask().setCurrentThread(null);
            }
        //if thread is waiting then it adds 1 to its waiting status
        } else if (this.getStatus() >= ThreadWaiting) {
            this.setStatus(this.getStatus() + 1);
        }

        //checks that the thread isn't in the list already then adds it to the list
        if (!readyList.contains(this)) {
            event.addThread(this);
        }
        dispatch();
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
        //checks if thread is running, calls dispatch & returns if so
        if (getStatus() == ThreadRunning) {
            dispatch();
            return;
        //checks if thread is waiting, sets status to ready & adds it to the list if so
        } else if (getStatus() == ThreadWaiting) {
            this.setStatus(ThreadReady);
            readyList.append(this);
        //for any other status, decreases its status number by 1
        } else {
            this.setStatus(getStatus() - 1);
        }
        dispatch();
    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
        //creates new thread
        ThreadCB thread = null;

        //try/catch block to catch a null return error
        try {
            //checks if the thread is running, if so it does nothing but returns SUCCESS
            if (MMU.getPTBR().getTask().getCurrentThread() != null) {
                return SUCCESS;
            }
        //for when there isn't a thread running so null return
        } catch (Exception e) {
                //checks if the readyList is empty, if so it sets register to null & returns FAILURE
                if (readyList.isEmpty()) {
                    MMU.setPTBR(null);
                    return FAILURE;
                /*if the list isn't empty, it creates a new thread object, sets it equal
                * to the thread at the beginning, sets its status to running, sets the
                * registers new page, sets the new thread to the current task, and returns SUCCESS
                */
                } else {
                    ThreadCB thread2 = (ThreadCB)readyList.removeHead();
                    thread2.setStatus(ThreadRunning);
                    MMU.setPTBR(thread2.getTask().getPageTable());
                    thread2.getTask().setCurrentThread(thread2);
                    return SUCCESS;
                }
        }
        return SUCCESS;
    }

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        //empty
    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        //empty
    }
}
