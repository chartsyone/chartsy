/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.trade;

import one.chartsy.trade.Order.State;

import java.io.Serial;

/**
 * Represents the order status change event.
 * 
 * @author Mariusz Bernacki
 *
 */
public class OrderStatusEvent extends java.util.EventObject {
    @Serial
    private static final long serialVersionUID = -7660946160463770478L;
    /** The new order status. */
    private final State newStatus;
    /** The old order status. */
    private final State oldStatus;
    
    
    public OrderStatusEvent(Order source, State oldStatus) {
        super(source);
        this.newStatus = source.getState();
        this.oldStatus = oldStatus;
    }

    public OrderStatusEvent(Order source) {
        super(source);
        this.newStatus = source.getState();
        this.oldStatus = source.getPreviousState();
    }

    @Override
    public Order getSource() {
        return (Order) super.getSource();
    }
    
    public State getNewStatus() {
        return newStatus;
    }
    
    public State getOldStatus() {
        return oldStatus;
    }
}