/**
 * @author andre
 *
 */
package com.google.code.magja.model.order;

import java.util.ArrayList;
import java.util.List;

import com.google.code.magja.model.BaseMagentoModel;
import com.google.code.magja.soap.ArrayItemMap;

public class Invoice extends BaseMagentoModel<Object[]> {

	private static final long serialVersionUID = 1812284345946352134L;

	private Integer customerId;

	private String orderNumber;

	private Double totalQty;

	private Integer shipmentId;

	private List<InvoiceItem> items = new ArrayList<InvoiceItem>();

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.google.code.magja.model.BaseMagentoModel#serializeToApi()
	 */
	@Override
	public Object[] serializeToApi() {
		// only create for now
		if (id == null) {
			ArrayItemMap map = new ArrayItemMap();
			for (InvoiceItem item : items) {
				map.add(item.getOrderItemId(), item.getQty().intValue());
			}

			return new Object[] { orderNumber, map };
		} else {
			return new Object[] {};
		}
	}

	/**
	 * @return the customerId
	 */
	public Integer getCustomerId() {
		return customerId;
	}

	/**
	 * @param customerId
	 *            the customerId to set
	 */
	public void setCustomerId(Integer customerId) {
		this.customerId = customerId;
	}

	/**
	 * @return the orderNumber
	 */
	public String getOrderNumber() {
		return orderNumber;
	}

	/**
	 * @param orderNumber
	 *            the orderNumber to set
	 */
	public void setOrderNumber(String orderNumber) {
		this.orderNumber = orderNumber;
	}

	/**
	 * @return the totalQty
	 */
	public Double getTotalQty() {
		return totalQty;
	}

	/**
	 * @param totalQty
	 *            the totalQty to set
	 */
	public void setTotalQty(Double totalQty) {
		this.totalQty = totalQty;
	}

	/**
	 * @return the shipmentId
	 */
	public Integer getInvoiceId() {
		return shipmentId;
	}

	/**
	 * @param shipmentId
	 *            the shipmentId to set
	 */
	public void setInvoiceId(Integer shipmentId) {
		this.shipmentId = shipmentId;
	}

	/**
	 * @return the items
	 */
	public List<InvoiceItem> getItems() {
		return items;
	}

	/**
	 * @param items
	 *            the items to set
	 */
	public void setItems(List<InvoiceItem> items) {
		this.items = items;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((customerId == null) ? 0 : customerId.hashCode());
		result = prime * result + ((items == null) ? 0 : items.hashCode());
		result = prime * result + ((orderNumber == null) ? 0 : orderNumber.hashCode());
		result = prime * result + ((shipmentId == null) ? 0 : shipmentId.hashCode());
		result = prime * result + ((totalQty == null) ? 0 : totalQty.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Invoice other = (Invoice) obj;
		if (customerId == null) {
			if (other.customerId != null)
				return false;
		} else if (!customerId.equals(other.customerId))
			return false;
		if (items == null) {
			if (other.items != null)
				return false;
		} else if (!items.equals(other.items))
			return false;
		if (orderNumber == null) {
			if (other.orderNumber != null)
				return false;
		} else if (!orderNumber.equals(other.orderNumber))
			return false;
		if (shipmentId == null) {
			if (other.shipmentId != null)
				return false;
		} else if (!shipmentId.equals(other.shipmentId))
			return false;
		if (totalQty == null) {
			if (other.totalQty != null)
				return false;
		} else if (!totalQty.equals(other.totalQty))
			return false;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Invoice [customerId=" + customerId + ", items=" + items + ", orderNumber=" + orderNumber + ", shipmentId=" + shipmentId + ", totalQty="
				+ totalQty + ", id=" + id + ", properties=" + properties + "]";
	}

}
