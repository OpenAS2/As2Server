package org.openas2.app.partner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.mail.smime.handlers.pkcs7_signature;
import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import edu.umd.cs.findbugs.log.Logger;
import io.sentry.instrumentation.file.SentryFileInputStream.Factory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

/**
 * adds a new partner entry in partnership store
 *
 * @author joseph mcverry
 */

public class AddPartnerCommand extends AliasedPartnershipsCommand {

    private Log logger = LogFactory.getLog(getClass().getName());

    public String getDefaultDescription() {
        return "Add a new partner to partnership store.";
    }

    public String getDefaultName() {

        return "add";
    }

    public String getDefaultUsage() {

        return "add name <attribute 1=value-1> <attribute 2=value-2> ... <attribute n=value-n>";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {

        logger.info("partFx=" + partFx.toString());
        // printStackTrace();

        if (params.length < 1) {

            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        // * the original
        synchronized (partFx) {

            DocumentBuilder db = null;
            try {
                db = DocumentBuilderFactory.newInstance().newDocumentBuilder();

                logger.info("Backing up " + db.getClass().getName());

            } catch (ParserConfigurationException e) {
                throw new OpenAS2Exception(e);
            } catch (FactoryConfigurationError e) {
                throw new OpenAS2Exception(e);
            } 

            
        

            Document doc = db.newDocument();
            logger.info("**** doc.getBaseUri()=" + doc.getBaseURI());
            logger.info("********doc.toString()=" + doc.toString());

            Element partnerRoot = doc.createElement("partner");
            logger.info("**** PartnerRoot.getLocalName()=" + partnerRoot.getLocalName());

            doc.appendChild(partnerRoot);
            logger.info("**** the doc=" + doc.toString());
            for (int i = 0; i < params.length; i++) {
                String param = (String) params[i];
                int pos = param.indexOf('=');
                if (i == 0) {
                    partnerRoot.setAttribute("name", param);
                } else if (pos == 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR,
                            "incoming parameter missing name");
                } else if (pos > 0) {
                    partnerRoot.setAttribute(param.substring(0, pos), param.substring(pos + 1));

                } else {
                    return new CommandResult(CommandResult.TYPE_ERROR,
                            "incoming parameter missing value");
                }

            }

            ((XMLPartnershipFactory) partFx).loadPartner(partFx.getPartners(),
                    partnerRoot);
            // Add the element to the already loaded partnership XML doc
            ((XMLPartnershipFactory) partFx).addElement(partnerRoot);

            return new CommandResult(CommandResult.TYPE_OK);
        }

    }
}
