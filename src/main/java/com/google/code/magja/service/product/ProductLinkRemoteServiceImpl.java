/**
 * @author andre
 *
 */
package com.google.code.magja.service.product;

import com.google.code.magja.magento.ResourcePath;
import com.google.code.magja.model.product.Product;
import com.google.code.magja.model.product.ProductLink;
import com.google.code.magja.model.product.ProductLink.LinkType;
import com.google.code.magja.model.product.ProductType;
import com.google.code.magja.service.GeneralServiceImpl;
import com.google.code.magja.service.ServiceException;
import com.google.code.magja.soap.MagentoSoapClient;

import org.apache.axis2.AxisFault;

import java.util.*;

public class ProductLinkRemoteServiceImpl extends GeneralServiceImpl<ProductLink> implements ProductLinkRemoteService {

	private static final long serialVersionUID = 28223743577747311L;

	public ProductLinkRemoteServiceImpl(MagentoSoapClient soapClient) {
		super(soapClient);
	}

	private ProductLink buildProductLink(Map<String, Object> map, LinkType linkType) {
		ProductLink link = new ProductLink();

		link.setLinkType(linkType);

		for (Map.Entry<String, Object> att : map.entrySet())
			link.set(att.getKey(), att.getValue());

		if (map.get("type") != null)
			link.setProductType(ProductType.getType((String) map.get("type")));

		return link;
	}

	private Boolean validateProductLink(ProductLink link) {
		if (link == null)
			return false;
		else if (link.getLinkType() != null && (link.getId() != null || link.getSku() != null))
			return true;
		else
			return false;
	}

	private Object[] buildLinkToPersist(Product product, ProductLink link) {
		Map<String, Object> props = new HashMap<String, Object>();
		if (link.getPosition() != null)
			props.put("position", link.getPosition());
		if (link.getQty() != null)
			props.put("qty", link.getQty());

		return new Object[] { link.getLinkType().toString().toLowerCase(), product.getId() != null ? product.getId() : product.getSku(),
				link.getId() != null ? link.getId() : link.getSku(), props };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.google.code.magja.service.product.ProductLinkRemoteService#assign
	 * (code .google.magja.model.product.Product,
	 * com.google.code.magja.model.product.ProductLink)
	 */
	@Override
	public void assign(Product product, ProductLink link) throws ServiceException {
		if (!ProductServiceUtil.validateProduct(product))
			throw new ServiceException("the product id or sku must be setted.");
		if (!validateProductLink(link))
			throw new ServiceException("you must specify the products to be assigned.");

		try {
			soapClient.callArgs(ResourcePath.ProductLinkAssign, buildLinkToPersist(product, link));
		} catch (AxisFault e) {
			if (debug)
				e.printStackTrace();
			throw new ServiceException(e.getMessage());
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.google.code.magja.service.product.ProductLinkRemoteService#list(code.
	 * google.magja.model.product.Product)
	 */
	@Override
	public Set<ProductLink> list(Product product) throws ServiceException {

		if (!ProductServiceUtil.validateProduct(product))
			throw new ServiceException("you must specify a product");

		Set<ProductLink> links = new HashSet<ProductLink>();

		for (LinkType linkType : LinkType.values())
			links.addAll(list(linkType, product));

		return links;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.google.code.magja.service.product.ProductLinkRemoteService#list(code.
	 * google.magja.model.product.ProductLink.LinkType,
	 * com.google.code.magja.model.product.Product)
	 */
	@Override
	public Set<ProductLink> list(LinkType linktype, Product product) throws ServiceException {

		if (!ProductServiceUtil.validateProduct(product))
			throw new ServiceException("you must specify a product");
		if (linktype == null)
			throw new ServiceException("you must specify a link type");

		Set<ProductLink> links = new HashSet<ProductLink>();

		List<Map<String, Object>> results = null;
		try {
			results = soapClient.callArgs(ResourcePath.ProductLinkList,
					new Object[] { linktype.toString().toLowerCase(), product.getId() != null ? product.getId() : product.getSku() });
		} catch (AxisFault e) {
			if (debug)
				e.printStackTrace();
			throw new ServiceException(e.getMessage());
		}

		if (results == null)
			return links;

		for (Map<String, Object> result : results)
			links.add(buildProductLink(result, linktype));

		return links;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.google.code.magja.service.product.ProductLinkRemoteService#remove
	 * (code .google.magja.model.product.Product,
	 * com.google.code.magja.model.product.ProductLink)
	 */
	@Override
	public void remove(Product product, ProductLink link) throws ServiceException {
		if (!ProductServiceUtil.validateProduct(product))
			throw new ServiceException("the product id or sku must be setted");
		if (!validateProductLink(link))
			throw new ServiceException("you must specify the products to be assigned");

		try {
			soapClient.callArgs(ResourcePath.ProductLinkRemove,
					new Object[] { link.getLinkType().toString().toLowerCase(), product.getId() != null ? product.getId() : product.getSku(),
							link.getId() != null ? link.getId() : link.getSku() });
		} catch (AxisFault e) {
			if (debug)
				e.printStackTrace();
			throw new ServiceException(e.getMessage());
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.google.code.magja.service.product.ProductLinkRemoteService#update
	 * (code .google.magja.model.product.Product,
	 * com.google.code.magja.model.product.ProductLink)
	 */
	@Override
	public void update(Product product, ProductLink link) throws ServiceException {

		if (!ProductServiceUtil.validateProduct(product))
			throw new ServiceException("the product id or sku must be setted");
		if (!validateProductLink(link))
			throw new ServiceException("you must specify the products to be assigned");

		try {
			soapClient.callArgs(ResourcePath.ProductLinkUpdate, buildLinkToPersist(product, link));
		} catch (AxisFault e) {
			if (debug)
				e.printStackTrace();
			throw new ServiceException(e.getMessage());
		}
	}

}
