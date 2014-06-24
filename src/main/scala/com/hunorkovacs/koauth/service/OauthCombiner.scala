package com.hunorkovacs.koauth.service

import java.net.URLEncoder
import com.hunorkovacs.koauth.domain.OauthParams.{tokenSecretName, verifierName, tokenName}

import scala.concurrent.{ExecutionContext, Future}
import com.hunorkovacs.koauth.domain._

object OauthCombiner {

  def urlEncode(s: String) = URLEncoder.encode(s, OauthExtractor.UTF8)
    .replaceAll("\\+", "%20")
    .replaceAll("\\*", "%2A")
    .replaceAll("%7E", "~")

  def concatItemsForSignature(request: EnhancedRequest)
                             (implicit ec: ExecutionContext): Future[String] = {
    normalizeOauthParamsForSignature(request.oauthParamsList) flatMap { n =>
      concatItems(List(request.method, request.urlWithoutParams, n))
    }
  }

  private def normalizeOauthParamsForSignature(allParamsList: List[(String, String)])
                                              (implicit ec: ExecutionContext): Future[String] = {
    Future {
      allParamsList filterNot { keyValue =>
        keyValue._1 == OauthParams.realmName && keyValue._1 == OauthParams.signatureName
      }
    } flatMap combineOauthParams
  }

  private def combineOauthParams(keyValueList: List[(String, String)])
                        (implicit ec: ExecutionContext): Future[String] = {
    Future {
      val paramsTogetherEncoded = keyValueList map { keyValue =>
        val (key, value) = keyValue
        urlEncode(key) + "=" + urlEncode(value)
      }
      paramsTogetherEncoded.sorted
    } flatMap concatItems
  }

  def concatItems(itemList: List[String])(implicit ec: ExecutionContext): Future[String] = {
    Future {
      itemList
        .map(item => urlEncode(item))
        .mkString("&")
    }
  }

  def createRequestTokenResponse(token: String, secret: String,callback: String)
                                (implicit ec: ExecutionContext): Future[OauthResponseOk] = {
    Future {
      List((tokenName, token),
        (tokenSecretName, secret),
        (OauthParams.callbackName, callback))
    }
      .flatMap(combineOauthParams)
      .map(body => new OauthResponseOk(body))
  }

  def createAuthorizeResponse(token: String, verifier: String)
                             (implicit ec: ExecutionContext): Future[OauthResponseOk] =
    combineOauthParams(List((tokenName, token), (verifierName, verifier)))
      .map(paramsString => new OauthResponseOk(paramsString))

  def createAccesTokenResponse(token: String, secret: String)
                              (implicit ec: ExecutionContext): Future[OauthResponseOk] =
    combineOauthParams(List((tokenName, token), (tokenSecretName, secret)))
      .map(body => new OauthResponseOk(body))
}
