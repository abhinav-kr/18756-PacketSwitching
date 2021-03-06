/**
 * @author andy
 * @version 1.0
 * @date 24-10-2008
 * @since 1.0
 */

package NetworkElements;

import java.util.ArrayList;
import java.util.Random;

import DataTypes.ATMCell;

public class ATMNIC {
	private IATMCellConsumer parent; // The router or computer that this nic is
										// in
	private OtoOLink link; // The link connected to this nic
	private boolean trace = false; // should we print out debug statements?
	private ArrayList<ATMCell> inputBuffer = new ArrayList<ATMCell>(); // Where
																		// cells
																		// are
																		// put
																		// between
																		// the
																		// parent
																		// and
																		// nic
	private ArrayList<ATMCell> outputBuffer = new ArrayList<ATMCell>(); // Where
																		// cells
																		// are
																		// put
																		// to be
																		// outputted
	private boolean tail = true, red = false, ppd = false, epd = false; // set
																		// what
																		// type
																		// of
																		// drop
																		// mechanism
	private int maximumBufferCells = 20; // the maximum number of cells in the
											// output buffer
	private int startDropAt = 10; // the minimum number of cells in the output
									// buffer before we start dropping cells
	private boolean keepDroppingUntilNextIPPacket = false;

	/**
	 * Default constructor for an ATM NIC
	 * 
	 * @param parent
	 * @since 1.0
	 */
	public ATMNIC(IATMCellConsumer parent) {
		this.parent = parent;
		this.parent.addNIC(this);
	}

	/**
	 * This method is called when a cell is passed to this nic to be sent. The
	 * cell is placed in an output buffer until a time unit passes
	 * 
	 * @param cell
	 *            the cell to be sent (placed in the buffer)
	 * @param parent
	 *            the router the cell came from
	 * @since 1.0
	 */
	public void sendCell(ATMCell cell, IATMCellConsumer parent) {
		if (this.trace) {
			System.out.println("Trace (ATM NIC): Received cell");
			if (this.link == null)
				System.out
						.println("Error (ATM NIC): You are trying to send a cell through a nic not connected to anything");
			if (this.parent != parent)
				System.out
						.println("Error (ATM NIC): You are sending data through a nic that this router is not connected to");
			if (cell == null)
				System.out
						.println("Warning (ATM NIC): You are sending a null cell");
		}

		if (this.tail)
			this.runTailDrop(cell);
		else if (this.red)
			this.runRED(cell);
		else if (this.ppd)
			this.runPPD(cell);
		else if (this.epd)
			this.runEPD(cell);
	}

	/**
	 * Runs tail drop on the cell
	 * 
	 * @param cell
	 *            the cell to be added/dropped
	 * @since 1.0
	 */
	private void runTailDrop(ATMCell cell) {
		boolean cellDropped = false;

		if (outputBuffer.size() < this.maximumBufferCells) {
			outputBuffer.add(cell);
		} else {
			cellDropped = true;
		}

		// Output to the console what happened
		if (cellDropped)
			System.out.println("The cell " + cell.getTraceID()
					+ " was tail dropped");
		else if (this.trace)
			System.out.println("The cell " + cell.getTraceID()
					+ " was added to the output queue");
	}

	/**
	 * Runs Random early detection on the cell
	 * 
	 * @param cell
	 *            the cell to be added/dropped from the queue
	 * @since 1.0
	 */
	private void runRED(ATMCell cell) {
		
		boolean cellDropped = false;
		double dropProbability = 0.0;
		boolean shouldDropCell = false;
		Random randomNumber = new Random();
		
		int currentOutputBufferSize = this.outputBuffer.size();
		System.out.println("currentOutputBufferSize :"+currentOutputBufferSize);
		dropProbability = (double)(currentOutputBufferSize-this.startDropAt)/(double)(this.maximumBufferCells-this.startDropAt);

		//Simulating the probability by generating a random number every time and checking if it falls  below the current buffer size
		//So, when the the buffer size is 11, probability is 0.1
		//when the the buffer size is 20, probability is 1
		
		if ( currentOutputBufferSize > this.startDropAt ) {
			System.out.println("Random number is:"+randomNumber.nextInt(Integer.MAX_VALUE));
			if ((randomNumber.nextInt(Integer.MAX_VALUE)%this.maximumBufferCells <= currentOutputBufferSize)) {
				shouldDropCell = true;
			}
		}
		
		if (!shouldDropCell) {
			outputBuffer.add(cell);
			cellDropped = false;
		} else {
			cellDropped = true;
		}

		// Output to the console what happened
		if (cellDropped)
			System.out.println("The cell " + cell.getTraceID()
					+ " was dropped with probability " + dropProbability);
		else if (this.trace)
			System.out.println("The cell " + cell.getTraceID()
					+ " was added to the output queue");
	}
	
	boolean IPHeaderIsInCell(ATMCell cell) {
		if(cell.getPacketData()!=null) {
			return true;
		}
		return false;
	}
	
	/**
	 * Runs Partial packet drop on the cell
	 * 
	 * @param cell
	 *            the cell to be added/dropped from the queue
	 * @since 1.0
	 */	
	private void runPPD(ATMCell cell) {
		boolean cellDropped = true;
		boolean shouldDropCell = false;
		Random randomNumber = new Random();
		
		int currentOutputBufferSize = this.outputBuffer.size();

		if ( currentOutputBufferSize > this.startDropAt ) {
			System.out.println("Random number is:"+randomNumber.nextInt(Integer.MAX_VALUE));
			if (randomNumber.nextInt(Integer.MAX_VALUE)%this.maximumBufferCells <= currentOutputBufferSize ) {
				shouldDropCell = true;
			}
		}
	
		//Next IP Packet has arrived. Stop dropping.
		if ( this.IPHeaderIsInCell(cell) && this.keepDroppingUntilNextIPPacket ) {
			this.keepDroppingUntilNextIPPacket = false;
		}
		
		//If the arriving cell has IPHeader and it should be dropped, then drop all the arriving cells of the same packet.
		if ( this.IPHeaderIsInCell(cell) && shouldDropCell && this.keepDroppingUntilNextIPPacket == false ) {
			this.keepDroppingUntilNextIPPacket = true;	
		}
		
		if (!this.keepDroppingUntilNextIPPacket) {
			if (shouldDropCell) {
				this.keepDroppingUntilNextIPPacket = true;						
				//Scan buffer for cells belonging to the current IPPacket and remove them.
				for(int i=currentOutputBufferSize-1; i>=0; i--) {
					if(IPHeaderIsInCell(this.outputBuffer.get(i))) {
						System.out.println("The cell " + this.outputBuffer.get(i).getTraceID() + " was dropped as they belonged to the same packet");
						this.outputBuffer.remove(i);
						break;
					}
					System.out.println("The cell " + this.outputBuffer.get(i).getTraceID() + " was dropped as they belonged to the same packet");
					this.outputBuffer.remove(i);
				}
			} else {
				outputBuffer.add(cell);
				cellDropped = false;
			}
		} 

		// Output to the console what happened
		if (cellDropped)
			System.out.println("The cell " + cell.getTraceID() + " was dropped");
		else if (this.trace)
			System.out.println("The cell " + cell.getTraceID()
					+ " was added to the output queue");
	}

	/**
	 * Runs Early packet drop on the cell
	 * 
	 * @param cell
	 *            the cell to be added/dropped from the queue
	 * @since 1.0
	 */
	private void runEPD(ATMCell cell) {
		
		boolean cellDropped = true;
		boolean willPacketBeDropped = false;
		Random randomNumber = new Random();
		
		//Next IP Packet has arrived. Stop dropping.
		if ( this.IPHeaderIsInCell(cell) && this.keepDroppingUntilNextIPPacket ) {
			this.keepDroppingUntilNextIPPacket = false;
		}
		
		if ( this.IPHeaderIsInCell(cell)) {
			int sizeOfPacket = cell.getPacketData().getSize();
			int noOfCells = (int) Math.ceil((double)sizeOfPacket/(48*8)); 
			int currentOutputBufferSize = this.outputBuffer.size();
			
			willPacketBeDropped = false;
			
			//After placing all the cells, check if there is a chance that one of the cells might be dropped
			if((currentOutputBufferSize + noOfCells)>this.startDropAt) {
				if ((randomNumber.nextInt(Integer.MAX_VALUE)%this.maximumBufferCells <= (currentOutputBufferSize + noOfCells))) {
					//If yes, drop all the cells of this packet until next packet arrives
					willPacketBeDropped = true;
					this.keepDroppingUntilNextIPPacket=true;
				}
			}
		}
		
		//Admit the cell into the buffer only if none of the cells of that packet will be dropped.
		if ( !willPacketBeDropped && !this.keepDroppingUntilNextIPPacket ) {
			cellDropped = false;
			outputBuffer.add(cell);
		}

		// Output to the console what happened
		if (cellDropped)
			System.out
					.println("The cell " + cell.getTraceID() + " was dropped");
		else if (this.trace)
			System.out.println("The cell " + cell.getTraceID()
					+ " was added to the output queue");
	}

	/**
	 * Sets that the nic should use Tail drop when deciding weather or not to
	 * add cells to the queue
	 * 
	 * @since 1.0
	 */
	public void setIsTailDrop() {
		this.red = false;
		this.tail = true;
		this.ppd = false;
		this.epd = false;
	}

	/**
	 * Sets that the nic should use RED when deciding weather or not to add
	 * cells to the queue
	 * 
	 * @since 1.0
	 */
	public void setIsRED() {
		this.red = true;
		this.tail = false;
		this.ppd = false;
		this.epd = false;
	}

	/**
	 * Sets that the nic should use PPD when deciding weather or not to add
	 * cells to the queue
	 * 
	 * @since 1.0
	 */
	public void setIsPPD() {
		this.red = false;
		this.tail = false;
		this.ppd = true;
		this.epd = false;
	}

	/**
	 * Sets that the nic should use EPD when deciding weather or not to add
	 * cells to the queue
	 * 
	 * @since 1.0
	 */
	public void setIsEPD() {
		this.red = false;
		this.tail = false;
		this.ppd = false;
		this.epd = true;
	}

	/**
	 * This method connects a link to this nic
	 * 
	 * @param link
	 *            the link to connect to this nic
	 * @since 1.0
	 */
	public void connectOtoOLink(OtoOLink link) {
		this.link = link;
	}

	/**
	 * This method is called when a cell is received over the link that this nic
	 * is connected to
	 * 
	 * @param cell
	 *            the cell that was received
	 * @since 1.0
	 */
	public void receiveCell(ATMCell cell) {
		this.inputBuffer.add(cell);

	}

	/**
	 * Moves the cells from the output buffer to the line (then they get moved
	 * to the next nic's input buffer)
	 * 
	 * @since 1.0
	 */
	public void clearOutputBuffers() {
		int line_rate = 10;
		for (int i = 0; i < Math.min(line_rate, this.outputBuffer.size()); i++)
			this.link.sendCell(this.outputBuffer.get(i), this);
		ArrayList<ATMCell> temp = new ArrayList<ATMCell>();
		for (int i = Math.min(line_rate, this.outputBuffer.size()); i < this.outputBuffer
				.size(); i++)
			temp.add(this.outputBuffer.get(i));
		this.outputBuffer.clear();
		this.outputBuffer.addAll(temp);
	}

	/**
	 * Moves cells from this nics input buffer to its output buffer
	 * 
	 * @since 1.0
	 */
	public void clearInputBuffers() {
		for (int i = 0; i < this.inputBuffer.size(); i++)
			this.parent.receiveCell(this.inputBuffer.get(i), this);
		this.inputBuffer.clear();
	}
}
