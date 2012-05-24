package org.nescent.informatics.phylotastic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import ca.wilkinsonlab.sadi.service.annotations.ContactEmail;
import ca.wilkinsonlab.sadi.service.annotations.InputClass;
import ca.wilkinsonlab.sadi.service.annotations.Name;
import ca.wilkinsonlab.sadi.service.annotations.OutputClass;
import ca.wilkinsonlab.sadi.service.simple.SimpleSynchronousServiceServlet;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

@Name("tree")
@ContactEmail("balhoff@nescent.org")
@InputClass("http://cdao.svn.sourceforge.net/svnroot/cdao/trunk/ontology/phylotastic/phylotastic_sadi.owl#TipCollection")
@OutputClass("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#RootedTree")
public class TreeService extends SimpleSynchronousServiceServlet {
	
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(TreeService.class);
	private static final long serialVersionUID = 1L;
	private static final int QUERY_CHUNK_SIZE = 50;
	private static final String endpoint = "http://pkb.nescent.org/sparql";
	private static final String ancestorQueryHead = 
			"PREFIX obo: <http://purl.obolibrary.org/obo/> " +
			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " + 
			"CONSTRUCT "+ 
			"{ ?super rdfs:subClassOf ?indirectSuper . ?indirectSuper rdfs:label ?label . } " + 
			"FROM <http://purl.obolibrary.org/obo/ncbitaxon.owl> " + 
			"WHERE " +
			"{ %s }";
	private static final String ancestorQueryBody = "{ ?super rdfs:subClassOf ?indirectSuper . <%s> rdfs:subClassOf ?super option(transitive) . OPTIONAL { ?indirectSuper rdfs:label ?label . } } ";
	private static final String parentQueryHead = 
			"PREFIX obo: <http://purl.obolibrary.org/obo/> " +
			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " + 
			"CONSTRUCT "+ 
			"{ ?taxon rdfs:subClassOf ?directSuper . ?taxon rdfs:label ?taxon_label . ?directSuper rdfs:label ?superLabel . } " + 
			"FROM <http://purl.obolibrary.org/obo/ncbitaxon.owl> " + 
			"WHERE " +
			"{ ?taxon rdfs:subClassOf ?directSuper . OPTIONAL { ?taxon rdfs:label ?taxon_label . } OPTIONAL { ?directSuper rdfs:label ?superLabel . } FILTER(?taxon IN (%s)) } ";
	private static final String parentQueryBody = "<%s>";

	@Override
	public void processInput(Resource input, Resource output) {
		final Model model = ModelFactory.createDefaultModel();
		final List<Statement> tipURIs = input.listProperties(Vocab.has).toList();
		final SublistIterator<Statement> tipsIterator = new SublistIterator<Statement>(tipURIs, QUERY_CHUNK_SIZE);
		while (tipsIterator.hasNext()) {
			final List<String> ancestorQueryBlocks = new ArrayList<String>();
			final List<String> parentQueryBlocks = new ArrayList<String>();
			final List<Statement> tips = tipsIterator.next();
			for (Statement statement : tips) {
				final String tipURI = statement.getResource().getProperty(DC.subject).getResource().getURI();
				ancestorQueryBlocks.add(String.format(ancestorQueryBody, tipURI));
				parentQueryBlocks.add(String.format(parentQueryBody, tipURI));
			}
			final String ancestorQuery = String.format(ancestorQueryHead, StringUtils.join(ancestorQueryBlocks, " UNION "));
			final String parentQuery = String.format(parentQueryHead, StringUtils.join(parentQueryBlocks, ", "));
			QueryEngineHTTP ancestorQE = new QueryEngineHTTP(endpoint, ancestorQuery);
			QueryEngineHTTP parentQE = new QueryEngineHTTP(endpoint, parentQuery);
			ancestorQE.execConstruct(model);
			ancestorQE.close();
			parentQE.execConstruct(model);
			parentQE.close();
		}
		this.processNode(Vocab.TaxonomyRoot, model, output.getModel(), null, output);
		output.getModel().setNsPrefix("cdao", "http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#");
	}
	
	private void processNode(Resource node, Model queryModel, Model outputModel, Resource parent, Resource tree) {
		final Set<Resource> children = queryModel.listResourcesWithProperty(RDFS.subClassOf, node).toSet();
		final Iterator<Resource> childIterator = children.iterator();
		if (children.size() == 1) {
			this.processNode(childIterator.next(), queryModel, outputModel, parent, tree);
		} else {
			final Resource blankNode = outputModel.createResource();
			if (parent != null) {
				outputModel.add(blankNode, Vocab.has_Parent, parent);
			} else {
				outputModel.add(tree, Vocab.has_Root, blankNode);
			}
			this.addLabel(blankNode, node, outputModel);
			outputModel.add(blankNode, RDF.type, Vocab.Node);
			outputModel.add(blankNode, DC.subject, node);
			if (children.size() > 1) {
				while (childIterator.hasNext()) {
					final Resource child = childIterator.next();
					this.processNode(child, queryModel, outputModel, blankNode, tree);
				}
			}
		}
	}
	
	private void addLabel(Resource blankNode, Resource queryNode, Model outputModel) {
		final Statement labelStatement = queryNode.getProperty(RDFS.label);
		if (labelStatement != null) {
			outputModel.add(blankNode, RDFS.label, labelStatement.getObject());
		}
	}

	@SuppressWarnings("unused")
	private static final class Vocab
	{
		private static Model m_model = ModelFactory.createDefaultModel();
		
		public static final Property part_of = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#part_of");
		public static final Property belongs_to_Edge_as_Child = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#belongs_to_Edge_as_Child");
		public static final Property has_Root = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#has_Root");
		public static final Property has_Node = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#has_Node");
		public static final Property has_Child_Node = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#has_Child_Node");
		public static final Property has_Annotation = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#has_Annotation");
		public static final Property subtree_of = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#subtree_of");
		public static final Property has_Parent_Node = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#has_Parent_Node");
		public static final Property has = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#has");
		public static final Property has_Parent = m_model.createProperty("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#has_Parent");
		public static final Resource RootedTree = m_model.createResource("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#RootedTree");
		public static final Resource EdgeAnnotation = m_model.createResource("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#EdgeAnnotation");
		public static final Resource Node = m_model.createResource("http://www.evolutionaryontology.org/cdao/1.0/cdao.owl#Node");
		public static final Resource Thing = m_model.createResource("http://www.w3.org/2002/07/owl#Thing");
		public static final Resource TipCollection = m_model.createResource("http://cdao.svn.sourceforge.net/svnroot/cdao/trunk/ontology/phylotastic/phylotastic_sadi.owl#TipCollection");
		public static final Resource TaxonomyRoot = m_model.createResource("http://purl.obolibrary.org/obo/NCBITaxon_1");
	}
	
}
