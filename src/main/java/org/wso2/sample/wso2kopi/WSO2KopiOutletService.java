/*
 *  Copyright (c) 2005-2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.sample.wso2kopi;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.wso2.sample.wso2kopi.beans.Order;
import org.wso2.sample.wso2kopi.beans.Payment;
import org.wso2.sample.wso2kopi.beans.PaymentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is the service class for the WSO2Kopi Platform
 */
@Path("/")
public class WSO2KopiOutletService {

    private Map<String, Order> ordersList = new ConcurrentHashMap<String, Order>();
    private Map<String, Payment> paymentRegister = new ConcurrentHashMap<String, Payment>();
    private final Map<String, Double> priceList = new ConcurrentHashMap<String, Double>();
    private static final Random rand = new Random();

    /**
     * This method adds a new order to the system
     *
     * @param orderBean contains the details of the order
     * @return The Response object built as either XML or JSON according to "Accept" header
     */
    @POST
    @Path("/order/")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response addOrder(Order orderBean) {
        String drinkName = orderBean.getDrinkName();
        String additions = orderBean.getAdditions();
        orderBean.setCost(calculateCost(drinkName, additions));
        ordersList.put(orderBean.getOrderId(), orderBean);
        return Response.ok(orderBean).build();
    }

    /**
     * This method returns the details of the method with the given orderid
     *
     * @param id the orderId of the order
     * @return The Response object built as either XML or JSON according to "Accept" header
     */
    @GET
    @Path("/order/{orderId}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Order getOrder(@PathParam("orderId") String id) {
        return ordersList.get(id);
    }

    /**
     * This method it used to update the oder
     *
     * @param id        the orderId of the order to be updated
     * @param orderBean The Response object built as either XML or JSON according to "Accept" header
     * @return
     */
    @PUT
    @Path("/order/{orderId}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response updateOrder(@PathParam("orderId") String id, Order orderBean) {
        String orderId = id;
        String drinkName = orderBean.getDrinkName();
        String additions = orderBean.getAdditions();

        Order order = ordersList.get(orderId);
        if (order != null) {
            if (order.isLocked()) {
                return Response.notModified().type(MediaType.APPLICATION_JSON_TYPE).build();
            } else {
                if (drinkName != null && !"".equals(drinkName)) {
                    order.setDrinkName(drinkName);
                } else {
                    drinkName = order.getDrinkName();
                }
                order.setAdditions(additions);
                order.setCost(calculateCost(drinkName, additions));
                return Response.ok(order).build();
            }
        }
        return null;
    }

    /**
     * This method is to retrieve all the orders
     *
     * @return The Response object built as either XML or JSON according to "Accept" header
     */
    @GET
    @Path("/orders")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response getPendingOrders() {
        List<Order> orders = new ArrayList<Order>();
        for (Order order : ordersList.values()) {
            if (!order.isLocked()) {
                orders.add(order);
            }
        }
        return Response.ok(orders.toArray(new Order[orders.size()])).build();
    }

    /**
     * This method is used to lock orders
     *
     * @param id the orderId of the order that needs to be locked
     * @return The Response object built as either XML or JSON according to "Accept" header
     */
    @PUT
    @Path("/order/lock/{orderId}/")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response lockOrder(@PathParam("orderId") String id) {
        Order order = ordersList.get(id);
        if (order != null) {
            order.setLocked(true);
            return Response.ok(order).build();
        }
        return Response.notModified().entity(id).build();
    }

    /**
     * This method is used to remove completed orders
     *
     * @param id the orderId of the order to be removed
     * @return The Response object built as either XML or JSON according to "Accept" header
     */
    @DELETE
    @Path("/order/{orderId}/")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response removeOrder(@PathParam("orderId") String id) {
        Boolean removed = ordersList.remove(id) != null;
        paymentRegister.remove(id);
        return removed ? Response.ok(removed).build() : Response.notModified().build();
    }

    /**
     * This method is used to do a payment to a given order
     *
     * @param id      the orderId of the order to which the payment is done
     * @param payment the payment object that holds the payment details
     * @return The Response object built as either XML or JSON according to "Accept" header
     */
    @POST
    @Path("/payment/{orderId}/")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response doPayment(@PathParam("orderId") String id, Payment payment) {
        String name = payment.getName();
        Double amount = payment.getAmount();
        String cardNumber = payment.getCardNumber();
        String expiryDate = payment.getExpiryDate();

        PaymentStatus paymentStatus;
        Payment registeredPayment = paymentRegister.get(id);
        if (registeredPayment != null) {
            paymentStatus = new PaymentStatus("Duplicate Payment", registeredPayment);
            return Response.notModified().entity(paymentStatus).build();
        }

        Order order = ordersList.get(id);
        if (order == null) {
            paymentStatus = new PaymentStatus("Invalid Order ID", null);
            return Response.notModified().entity(paymentStatus).build();
        }

        if (!order.isAmountAcceptable(amount)) {
            paymentStatus = new PaymentStatus("Insufficient Funds", null);
            return Response.notModified().entity(paymentStatus).build();
        }

        registeredPayment = new Payment(id);
        registeredPayment.setAmount(amount);
        registeredPayment.setCardNumber(cardNumber);
        registeredPayment.setExpiryDate(expiryDate);
        registeredPayment.setName(name);
        paymentRegister.put(id, registeredPayment);
        paymentStatus = new PaymentStatus("Payment Accepted", registeredPayment);
        return Response.ok().entity(paymentStatus).build();
    }

    /**
     * This method is used to review the payments that have been done
     *
     * @param id the orderId of the order to be reviewed
     * @return The Response object built as either XML or JSON according to "Accept" header
     */
    @GET
    @Path("/payment/{orderId}/")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
    public Response getPayment(@PathParam("orderId") String id) {
        return Response.ok().entity(paymentRegister.get(id)).build();
    }

    /**
     * This is a utility method to calculate the cost of a drink
     *
     * @param drinkName the name of the drink
     * @param additions the additions to the drink
     * @return the calculated price
     */
    private double calculateCost(String drinkName, String additions) {
        double cost = getPrice(drinkName, false);
        if (additions != null && !"".equals(additions)) {
            String[] additionalItems = additions.split(" ");
            for (String item : additionalItems) {
                cost += getPrice(item, true);
            }
        }
        return Double.parseDouble(Order.currencyFormat.format(cost));
    }

    /**
     * This method is a utility method used to help calculate the price of a drink
     *
     * @param item     the name of the drink
     * @param addition whether additions are present or not
     * @return the price of the item
     */
    private double getPrice(String item, boolean addition) {
        synchronized (priceList) {
            Double price = priceList.get(item);
            if (price == null) {
                if (addition) {
                    price = rand.nextDouble() * 5;
                } else {
                    price = rand.nextInt(8) + 2 - 0.01;
                }
                priceList.put(item, price);
            }
            return price;
        }
    }

}
